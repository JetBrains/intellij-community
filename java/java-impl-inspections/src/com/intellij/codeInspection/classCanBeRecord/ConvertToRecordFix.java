// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.PsiAnnotation.TargetType;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.memory.InnerClassReferenceVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.*;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.psi.PsiModifier.*;

public final class ConvertToRecordFix implements LocalQuickFix {
  private final boolean mySuggestAccessorsRenaming;
  private final @NotNull List<String> myIgnoredAnnotations;

  public ConvertToRecordFix(boolean suggestAccessorsRenaming, @NotNull List<String> ignoredAnnotations) {
    mySuggestAccessorsRenaming = suggestAccessorsRenaming;
    myIgnoredAnnotations = ignoredAnnotations;
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return JavaBundle.message("class.can.be.record.quick.fix");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final ConvertToRecordProcessor processor = getRecordProcessor(descriptor);
    if (processor == null) return;
    // Without the next line, the conflicts view is not shown
    processor.setPrepareSuccessfulSwingThreadCallback(() -> {
    });
    processor.run();
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    final ConvertToRecordProcessor processor = getRecordProcessor(previewDescriptor);
    if (processor == null) return IntentionPreviewInfo.EMPTY;

    // We can't use the below here, because BaseRefactoringProcessor#doRun calls PsiDocumentManager#commitAllDocumentsUnderProgress,
    //  and its Javadoc says "must be called on UI thread".
    // processor.run();

    processor.performRefactoring(UsageInfo.EMPTY_ARRAY);

    return IntentionPreviewInfo.DIFF;
  }

  private @Nullable ConvertToRecordProcessor getRecordProcessor(ProblemDescriptor descriptor) {
    PsiElement psiElement = descriptor.getPsiElement();
    if (psiElement == null || !psiElement.isValid()) return null;
    PsiClass psiClass = ObjectUtils.tryCast(psiElement.getParent(), PsiClass.class);
    if (psiClass == null) return null;

    RecordCandidate recordCandidate = tryCreateRecordCandidate(psiClass, mySuggestAccessorsRenaming, myIgnoredAnnotations);
    if (recordCandidate == null) return null;

    return new ConvertToRecordProcessor(recordCandidate, mySuggestAccessorsRenaming);
  }

  /**
   * There are some restrictions for records:
   * <a href="https://docs.oracle.com/javase/specs/jls/se15/preview/specs/records-jls.html">see the specification</a>.
   */
  static @Nullable RecordCandidate tryCreateRecordCandidate(@NotNull PsiClass psiClass,
                                                            boolean suggestAccessorsRenaming,
                                                            @NotNull List<String> ignoredAnnotations) {
    boolean isNotAppropriatePsiClass = psiClass.isEnum() ||
                                       psiClass.isAnnotationType() ||
                                       psiClass instanceof PsiAnonymousClass ||
                                       psiClass.isInterface() ||
                                       psiClass.isRecord();
    if (isNotAppropriatePsiClass) return null;

    PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList == null || modifierList.hasModifierProperty(ABSTRACT) || modifierList.hasModifierProperty(SEALED)) return null;
    if (PsiUtil.isLocalClass(psiClass) && containsOuterNonStaticReferences(psiClass)) return null;
    if (psiClass.getContainingClass() != null && !psiClass.hasModifierProperty(STATIC)) return null;

    PsiClass superClass = psiClass.getSuperClass();
    if (superClass == null || !JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) return null;

    if (ContainerUtil.exists(psiClass.getInitializers(), initializer -> !initializer.hasModifierProperty(STATIC))) return null;

    if (AnnotationUtil.checkAnnotatedUsingPatterns(psiClass, ignoredAnnotations)) return null;

    RecordCandidate result = new RecordCandidate(psiClass, suggestAccessorsRenaming);
    if (!result.isValid()) return null;

    boolean hasInheritors = ClassInheritorsSearch.search(psiClass, false).findFirst() != null;
    if (hasInheritors) return null;
    return result;
  }

  /**
   * @see com.siyeh.ig.memory.InnerClassMayBeStaticInspection
   */
  private static boolean containsOuterNonStaticReferences(PsiClass psiClass) {
    InnerClassReferenceVisitor visitor = new InnerClassReferenceVisitor(psiClass, false);
    psiClass.accept(visitor);
    return !visitor.canInnerClassBeStatic();
  }

  /**
   * Encapsulates necessary information about the class being converted: its fields, accessors, etc.
   * It helps to validate whether a class will be a well-formed record and supports performing a refactoring.
   */
  static class RecordCandidate {
    private static final CallMatcher OBJECT_METHOD_CALLS =
      CallMatcher.anyOf(CallMatcher.exactInstanceCall(JAVA_LANG_OBJECT, "equals").parameterCount(1),
                        CallMatcher.exactInstanceCall(JAVA_LANG_OBJECT, "hashCode", "toString").parameterCount(0));
    private final boolean mySuggestAccessorsRenaming;
    private final @NotNull PsiClass myClass;
    private final MultiMap<PsiField, FieldAccessorCandidate> myFieldsToAccessorCandidates = new MultiMap<>(new LinkedHashMap<>());
    private final Map<PsiMethod, @Nullable RecordConstructorCandidate> myMethodsToConstructorCandidates = new HashMap<>();
    private final List<PsiMethod> myOrdinaryMethods = new SmartList<>();

    private @Nullable PsiMethod myEqualsMethod;
    private @Nullable PsiMethod myHashCodeMethod;

    private Map<PsiField, FieldAccessorCandidate> myFieldAccessorsCache;

    private RecordCandidate(@NotNull PsiClass psiClass, boolean suggestAccessorsRenaming) {
      myClass = psiClass;
      mySuggestAccessorsRenaming = suggestAccessorsRenaming;
      prepare();
    }

    @NotNull Project getProject() {
      return myClass.getProject();
    }

    @NotNull PsiClass getPsiClass() {
      return myClass;
    }

    @UnmodifiableView
    @NotNull Map<PsiField, @Nullable FieldAccessorCandidate> getFieldsToAccessorCandidates() {
      if (myFieldAccessorsCache != null) return myFieldAccessorsCache;

      Map<PsiField, FieldAccessorCandidate> result = new LinkedHashMap<>();
      for (var entry : myFieldsToAccessorCandidates.entrySet()) {
        PsiField newKey = entry.getKey();
        Collection<FieldAccessorCandidate> oldValue = entry.getValue();
        FieldAccessorCandidate newValue = ContainerUtil.getOnlyItem(oldValue);
        result.put(newKey, newValue);
      }
      myFieldAccessorsCache = Collections.unmodifiableMap(result);
      return result;
    }

    /// Make sure that [#isValid] returns true before calling this method.
    ///
    /// @return the canonical constructor candidate, or null if there is no such constructor
    /// @throws IllegalStateException if there is more than 1 possible canonical constructor (usually, the code is red in this case)
    @Nullable RecordConstructorCandidate getCanonicalConstructorCandidate() {
      RecordConstructorCandidate result = null;
      for (RecordConstructorCandidate candidate : myMethodsToConstructorCandidates.values()) {
        if (candidate == null) continue;
        // I deem the tiny cost of iterating all of this map's entries worth
        // the benefit of validating the invariant "only 1 constructor candidate can be canonical".
        if (candidate.kind() == RecordConstructorCandidate.Kind.CANONICAL) {
          if (result != null) {
            String constructor1 = HighlightMessageUtil.getSymbolName(candidate.constructor());
            String constructor2 = HighlightMessageUtil.getSymbolName(result.constructor());

            // Cannot determine which is canonical.
            throw new IllegalStateException(
              "there can be only 1 canonical constructor candidate (found " + constructor1 + " and " + constructor2 + ")");
          }

          result = candidate;
        }
      }
      return result;
    }

    @NotNull Map<PsiMethod, RecordConstructorCandidate> getMethodsToConstructorCandidates() {
      return myMethodsToConstructorCandidates;
    }

    @Nullable PsiMethod getEqualsMethod() {
      return myEqualsMethod;
    }

    @Nullable PsiMethod getHashCodeMethod() {
      return myHashCodeMethod;
    }

    private boolean isValid() {
      int possibleCanonicalConstructorCount = 0;
      for (var constructorCandidate : myMethodsToConstructorCandidates.values()) {
        if (constructorCandidate == null) return false; // the constructor was invalid
        if (!throwsOnlyUncheckedExceptions(constructorCandidate.constructor())) return false;
        if (containsObjectMethodCalls(constructorCandidate.constructor())) return false;
        if (constructorCandidate.kind == RecordConstructorCandidate.Kind.CANONICAL) {
          for (PsiParameter parameter : constructorCandidate.constructor().getParameterList().getParameters()) {
            if (!constructorCandidate.paramsToFields().containsKey(parameter)) continue;
            PsiField field = constructorCandidate.paramsToFields.get(parameter);
            if (field == null) {
              boolean fieldWithMatchingNameExists = myClass.findFieldByName(parameter.getName(), false) != null;
              if (!fieldWithMatchingNameExists) return false;
            }
          }

          possibleCanonicalConstructorCount++;
        }
      }
      if (!myMethodsToConstructorCandidates.isEmpty() && possibleCanonicalConstructorCount != 1) return false;

      if (myFieldsToAccessorCandidates.size() == 0) return false;
      for (var entry : myFieldsToAccessorCandidates.entrySet()) {
        PsiField field = entry.getKey();
        if (!field.hasModifierProperty(FINAL)) return false;
        if (field.hasInitializer()) return false;
        if (JavaPsiRecordUtil.ILLEGAL_RECORD_COMPONENT_NAMES.contains(field.getName())) return false;
        if (entry.getValue().size() > 1) return false;
        FieldAccessorCandidate firstAccessor = ContainerUtil.getFirstItem(entry.getValue());
        if (firstAccessor == null) continue;
        if (containsObjectMethodCalls(firstAccessor.method())) return false;
      }
      for (PsiMethod ordinaryMethod : myOrdinaryMethods) {
        if (ordinaryMethod.hasModifierProperty(NATIVE)) return false;
        boolean conflictsWithPotentialAccessor = ordinaryMethod.getParameterList().isEmpty() &&
                                                 ContainerUtil.exists(myFieldsToAccessorCandidates.keySet(),
                                                                      field -> field.getName().equals(ordinaryMethod.getName()));
        if (conflictsWithPotentialAccessor) return false;
        if (containsObjectMethodCalls(ordinaryMethod)) return false;
      }
      return true;
    }

    private void prepare() {
      for (PsiField field : myClass.getFields()) {
        if (!field.hasModifierProperty(STATIC)) myFieldsToAccessorCandidates.put(field, new ArrayList<>());
      }

      for (PsiMethod method : myClass.getMethods()) {
        if (method.isConstructor()) {
          // Here, keySet() has a consistent iteration order because this specific MultiMap uses LinkedHashMap under the hood.
          final var orderedInstanceFields = new ArrayList<>(myFieldsToAccessorCandidates.keySet());
          RecordConstructorCandidate recordConstructorCandidate = tryCreateRecordConstructorCandidate(method, orderedInstanceFields);
          myMethodsToConstructorCandidates.put(method, recordConstructorCandidate);
        }
        else if (MethodUtils.isEquals(method)) {
          myEqualsMethod = method;
        }
        else if (MethodUtils.isHashCode(method)) {
          myHashCodeMethod = method;
        }
        else if (!throwsOnlyUncheckedExceptions(method)) {
          myOrdinaryMethods.add(method);
        }
        else {
          FieldAccessorCandidate fieldAccessorCandidate = tryCreateFieldAccessorCandidate(method);
          if (fieldAccessorCandidate == null) {
            myOrdinaryMethods.add(method);
          }
          else {
            myFieldsToAccessorCandidates.putValue(fieldAccessorCandidate.backingField, fieldAccessorCandidate);
          }
        }
      }
    }

    private static boolean throwsOnlyUncheckedExceptions(@NotNull PsiMethod psiMethod) {
      for (PsiClassType throwsType : psiMethod.getThrowsList().getReferencedTypes()) {
        if (throwsType == null) continue;
        if (!ExceptionUtil.isUncheckedException(throwsType)) {
          return false;
        }
      }
      return true;
    }

    private static boolean containsObjectMethodCalls(@NotNull PsiMethod psiMethod) {
      var visitor = new JavaRecursiveElementWalkingVisitor() {
        boolean existsSuperMethodCalls;

        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          if (hasSuperQualifier(expression.getMethodExpression()) && OBJECT_METHOD_CALLS.test(expression)) {
            existsSuperMethodCalls = true;
            stopWalking();
          }
        }

        @Override
        public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
          super.visitMethodReferenceExpression(expression);
          if (hasSuperQualifier(expression) && OBJECT_METHOD_CALLS.methodReferenceMatches(expression)) {
            existsSuperMethodCalls = true;
            stopWalking();
          }
        }

        private static boolean hasSuperQualifier(@NotNull PsiReferenceExpression expression) {
          PsiElement qualifier = expression.getQualifier();
          return qualifier != null && JavaKeywords.SUPER.equals(qualifier.getText());
        }
      };
      psiMethod.accept(visitor);
      return visitor.existsSuperMethodCalls;
    }

    private @Nullable FieldAccessorCandidate tryCreateFieldAccessorCandidate(@NotNull PsiMethod psiMethod) {
      if (psiMethod.hasModifier(JvmModifier.STATIC)) return null;
      if (!psiMethod.getParameterList().isEmpty()) return null;
      String methodName = psiMethod.getName();
      PsiField backingField = null;
      boolean recordStyleNaming = false;
      for (PsiField field : myFieldsToAccessorCandidates.keySet()) {
        if (!field.getType().equals(psiMethod.getReturnType())) continue;
        String fieldName = field.getName();
        if (fieldName.equals(methodName)) {
          backingField = field;
          recordStyleNaming = true;
          break;
        }
        if (mySuggestAccessorsRenaming &&
            fieldName.equals(PropertyUtilBase.getPropertyNameByGetter(psiMethod)) &&
            !ContainerUtil.exists(psiMethod.findDeepestSuperMethods(),
                                  superMethod -> superMethod instanceof PsiCompiledElement || superMethod instanceof SyntheticElement)) {
          backingField = field;
          break;
        }
      }
      return backingField == null ? null : new FieldAccessorCandidate(psiMethod, backingField, recordStyleNaming);
    }

    /// If null is returned, it means that there are problems with the constructor, such as
    /// - the constructor being generic,
    /// - unresolved references ("red code"),
    /// - or simply the constructor being too complex to convert / not implemented yet.
    ///
    /// In these cases, the class-to-record conversion cannot be performed.
    private static @Nullable RecordConstructorCandidate tryCreateRecordConstructorCandidate(@NotNull PsiMethod constructorMethod,
                                                                                            List<PsiField> instanceFields) {
      if (constructorMethod.getTypeParameters().length > 0) {
        return null;
      }

      final PsiCodeBlock ctorBody = constructorMethod.getBody();
      if (ctorBody == null) {
        return null;
      }

      final var bodyProcessor = new ConstructorBodyProcessor(constructorMethod, instanceFields);
      final boolean canonical = bodyProcessor.isCanonical();
      final boolean invalid = bodyProcessor.isTooComplex() ||
                              bodyProcessor.hasUnresolvedRefs() ||
                              (!canonical && bodyProcessor.hasAnyStatementBeforeAllFieldsAreAssigned());
      final boolean delegating = bodyProcessor.isDelegating();

      RecordConstructorCandidate.Kind type;
      if (invalid) {
        return null;
      }
      else if (canonical) {
        type = RecordConstructorCandidate.Kind.CANONICAL;
      }
      else if (delegating) {
        type = RecordConstructorCandidate.Kind.DELEGATING;
      }
      else {
        type = RecordConstructorCandidate.Kind.CUSTOM;
      }

      final Set<PsiStatement> otherStatements = new HashSet<>(bodyProcessor.getOtherStatements());
      return new RecordConstructorCandidate(
        type, constructorMethod, bodyProcessor.getParamsToFields(), bodyProcessor.getFieldNamesToInitializers(), otherStatements
      );
    }
  }

  /**
   * Encapsulates information about converting of a single constructor, for example, whether it is canonical or not.
   */
  @NotNullByDefault
  record RecordConstructorCandidate(
    Kind kind,
    PsiMethod constructor,
    Map<PsiParameter, @Nullable PsiField> paramsToFields,
    LinkedHashMap<String, PsiExpression> fieldNamesToInitializers, // TODO(bartekpacia): change type to SequencedMap once we move to Java 21
    Set<PsiStatement> otherStatements
  ) {

    /// The "kind" of record constructor that [#constructor] will take after being converted to a record (if at all).
    enum Kind {
      /// A constructor that could be converted to a record canonical constructor:
      /// its signature matches the number and types of fields, and it assigns all instance fields directly.
      /// See JLS 8.10.4.
      ///
      /// Becomes a canonical constructor during the class-to-record conversion.
      CANONICAL,
      /// A constructor that invokes an alternate constructor with `this()`
      /// (a technique also known as "constructor redirecting" or "constructor telescoping").
      /// See JLS 8.8.7.1.
      ///
      /// Copied as-is during the class-to-record conversion.
      DELEGATING,

      /// Constructor which is not canonical and not delegating, but assigns all instance fields directly.
      ///
      /// Becomes a delegating constructor during the class-to-record conversion.
      CUSTOM,
    }

    //@formatter:off Temporarily disable formatter because of bug IDEA-371809
    /// Maps each formal parameter of the constructor to the instance field it is assigned to.
    ///
    /// Allows for conversion to record when constructor parameter names aren't equal to instance field names.
    ///
    /// ### Example
    ///
    /// ```java
    /// class Point {
    ///   final double x;
    ///   final double y;
    /// 
    ///   Point(double x, double second) {
    ///     this.x = x;
    ///     this.y = second;
    ///   }
    /// }
    /// ```
    /// results in the following map:
    /// ```
    /// PsiParameter:x -> PsiField:x
    /// PsiParameter:second -> PsiField:y
    ///```
    //@formatter:on
    @Override
    public @UnmodifiableView Map<PsiParameter, @Nullable PsiField> paramsToFields() {
      return Collections.unmodifiableMap(paramsToFields);
    }

    //@formatter:off Temporarily disable formatter because of bug IDEA-371809
    /// Maps the name of each field (that is assigned to in the constructor body) to the [PsiExpression] it is assigned to.
    ///
    /// Allows for conversion to record when the constructor is not canonical and not delegating,
    /// i.e., it assigns to all required fields directly.
    ///
    /// ### Example
    /// 
    /// ```java
    /// class Point {
    ///   final double x;
    ///   final double y;
    ///   final double z;
    ///
    ///   Point(double x) {
    ///     this.x = x;
    ///     this.y = 0;
    ///     this.z = Integer.parseInt("42");
    ///   }
    /// }
    /// ```
    /// results in the following map:
    /// ```
    /// "x" -> PsiReferenceExpression:x
    /// "y" -> PsiLiteralExpression:0
    /// "z" -> PsiMethodCallExpression:Integer.parseInt("42")
    /// ```
    /// 
    /// ### How it is used
    /// 
    /// Contents of this map are used by [RecordBuilder] to generate the correct redirecting constructor call.
    /// 
    /// Before:
    /// ```java
    /// Point(double x) {
    ///   this.x = x;
    ///   this.y = 0;
    ///   this.z = Integer.parseInt("42");
    /// }
    /// ```
    /// 
    /// After:
    /// ```java
    /// Point(double x) {
    ///   this(x, 0, Integer.parseInt("42");
    /// }
    /// ```
    // @formatter:on
    @Override
    public @UnmodifiableView LinkedHashMap<String, PsiExpression> fieldNamesToInitializers() {
      return fieldNamesToInitializers;
    }

    //@formatter:off Temporarily disable formatter because of bug IDEA-371809
    /// Set of statements which are not assignments to instance fields or calls to constructors.
    ///
    /// ### Example
    /// 
    /// For the single constructor in the following class:
    ///
    /// ```java
    /// class Point {
    ///   final double x;
    ///   final double y;
    ///
    ///   Point(double x, double y) {
    ///     System.out.println("ctor: before fields are assigned");
    ///     this.x = x;
    ///     this.y = y;
    ///     System.out.println("ctor: after fields are assigned");
    ///   }
    /// }
    /// ```
    /// this set holds the two [PsiExpressionStatement]s that call `System.out.println`.
    /// 
    /// Order is not important.
    // @formatter:on
    @Override
    public @UnmodifiableView Set<PsiStatement> otherStatements() {
      return Collections.unmodifiableSet(otherStatements);
    }
  }

  /// Represents a candidate for an _accessor method_ during class-to-record conversion.
  /// See JLS 8.10.3.
  ///
  /// This class models the relationship between a field and its accessor method.
  /// The accessor method can be a:
  /// - a traditional getter (e.g., `getValue()` when `usesRecordStyleNaming` is `true`), or
  /// - a record-style accessor (e.g., `value()` when `usesRecordStyleNaming` is `false`).
  ///
  /// The default accessor method (when `isDefault` is `true`) is one that simply returns the field value without additional
  /// logic and doesn't have documentation and annotation conflicts that would prevent its removal.
  /// Since records automatically generate accessor methods for their
  /// components, these default accessors become redundant after conversion.
  ///
  /// The class also tracks naming style to support both traditional getter methods
  /// (e.g., `getValue()`) and record-style accessors (e.g., `value()`).
  ///
  /// @param method                The accessor method for the field
  /// @param backingField          The field being accessed
  /// @param isDefault             Whether this is a default accessor that is redundant after conversion and can be removed
  /// @param usesRecordStyleNaming Whether the accessor uses record-style naming (method name equals field name)
  @NotNullByDefault
  record FieldAccessorCandidate(PsiMethod method, PsiField backingField, boolean isDefault, boolean usesRecordStyleNaming) {
    private FieldAccessorCandidate(PsiMethod accessor, PsiField backingField, boolean recordStyleNaming) {
      this(accessor, backingField, calculateDefault(accessor, backingField), recordStyleNaming);
    }

    private static boolean calculateDefault(PsiMethod accessor, PsiField backingField) {
      if (accessor.getDocComment() != null) {
        return false;
      }
      final PsiExpression returnExpr = PropertyUtilBase.getSingleReturnValue(accessor);
      boolean isDefaultAccessor = backingField.equals(PropertyUtil.getFieldOfGetter(accessor, () -> returnExpr, false));
      if (!isDefaultAccessor) {
        return false;
      }
      isDefaultAccessor = !hasAnnotationConflict(accessor, backingField, TargetType.FIELD) &&
                          !hasAnnotationConflict(backingField, accessor, TargetType.METHOD);
      return isDefaultAccessor;
    }

    /**
     * During record creation we have to move the field annotations to the record component.
     * For instance, if an annotation's target includes both field and method target,
     * then we have to check whether the method is already marked by this annotation
     * as a compiler propagates annotations of the record components to appropriate targets automatically.
     */
    private static boolean hasAnnotationConflict(PsiModifierListOwner first, PsiModifierListOwner second, TargetType targetType) {
      boolean result = false;
      for (final PsiAnnotation firstAnn : first.getAnnotations()) {
        final TargetType firstAnnTarget = AnnotationTargetUtil.findAnnotationTarget(firstAnn, targetType);
        final boolean hasDesiredTarget = firstAnnTarget != null && firstAnnTarget != TargetType.UNKNOWN;
        if (!hasDesiredTarget) continue;
        if (!ContainerUtil.exists(second.getAnnotations(), secondAnn -> AnnotationUtil.equal(firstAnn, secondAnn))) {
          result = true;
          break;
        }
      }
      return result;
    }
  }
}
