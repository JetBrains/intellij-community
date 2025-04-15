// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.PsiAnnotation.TargetType;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.memory.InnerClassReferenceVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;
import static com.intellij.psi.PsiModifier.*;

public class ConvertToRecordFix extends InspectionGadgetsFix {
  private final boolean mySuggestAccessorsRenaming;
  private final @NotNull List<String> myIgnoredAnnotations;

  ConvertToRecordFix(boolean suggestAccessorsRenaming, @NotNull List<String> ignoredAnnotations) {
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
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
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
    if (psiElement == null) return null;
    PsiClass psiClass = ObjectUtils.tryCast(psiElement.getParent(), PsiClass.class);
    if (psiClass == null) return null;

    RecordCandidate recordCandidate = getClassDefinition(psiClass, mySuggestAccessorsRenaming, myIgnoredAnnotations);
    if (recordCandidate == null) return null;

    return new ConvertToRecordProcessor(recordCandidate);
  }

  /**
   * There are some restrictions for records:
   * <a href="https://docs.oracle.com/javase/specs/jls/se15/preview/specs/records-jls.html">see the specification</a>.
   */
  static RecordCandidate getClassDefinition(@NotNull PsiClass psiClass,
                                            boolean suggestAccessorsRenaming,
                                            @NotNull List<String> ignoredAnnotations) {
    boolean isNotAppropriatePsiClass = psiClass.isEnum() ||
                                       psiClass.isAnnotationType() ||
                                       psiClass instanceof PsiAnonymousClass ||
                                       psiClass.isInterface() ||
                                       psiClass.isRecord();
    if (isNotAppropriatePsiClass) return null;

    PsiModifierList psiClassModifiers = psiClass.getModifierList();
    if (psiClassModifiers == null || psiClassModifiers.hasModifierProperty(ABSTRACT) || psiClassModifiers.hasModifierProperty(SEALED)) {
      return null;
    }
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
   * Encapsulates necessary information about the converting class e.g its existing fields, accessors...
   * It helps to validate whether a class will be a well-formed record and supports performing a refactoring.
   */
  static class RecordCandidate {
    private static final CallMatcher OBJECT_METHOD_CALLS =
      CallMatcher.anyOf(CallMatcher.exactInstanceCall(JAVA_LANG_OBJECT, "equals").parameterCount(1),
                        CallMatcher.exactInstanceCall(JAVA_LANG_OBJECT, "hashCode", "toString").parameterCount(0));
    private final PsiClass myClass;
    private final boolean mySuggestAccessorsRenaming;
    private final MultiMap<PsiField, FieldAccessorCandidate> myFieldsToAccessorCandidates = new MultiMap<>(new LinkedHashMap<>());
    private final List<PsiMethod> myOrdinaryMethods = new SmartList<>();
    final List<RecordConstructorCandidate> myConstructorCandidates = new SmartList<>();

    private PsiMethod myEqualsMethod;
    private PsiMethod myHashCodeMethod;

    private Map<PsiField, FieldAccessorCandidate> myFieldAccessorsCache;

    private RecordCandidate(@NotNull PsiClass psiClass, boolean suggestAccessorsRenaming) {
      myClass = psiClass;
      mySuggestAccessorsRenaming = suggestAccessorsRenaming;
      prepare();
    }

    Project getProject() {
      return myClass.getProject();
    }

    PsiClass getPsiClass() {
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

    @Nullable RecordConstructorCandidate getCanonicalConstructorCandidate() {
      return myConstructorCandidates.size() == 1 ? myConstructorCandidates.get(0) : null;
    }

    @Nullable PsiMethod getEqualsMethod() {
      return myEqualsMethod;
    }

    @Nullable PsiMethod getHashCodeMethod() {
      return myHashCodeMethod;
    }

    private boolean isValid() {
      if (myConstructorCandidates.size() > 1) return false;
      if (myConstructorCandidates.size() == 1) {
        RecordConstructorCandidate ctorCandidate = myConstructorCandidates.get(0);
        boolean isCanonical = ctorCandidate.canonical && throwsOnlyUncheckedExceptions(ctorCandidate.constructorMethod);
        if (!isCanonical) return false;
        if (containsObjectMethodCalls(ctorCandidate.constructorMethod)) return false;
      }
      if (myFieldsToAccessorCandidates.size() == 0) return false;
      for (var entry : myFieldsToAccessorCandidates.entrySet()) {
        PsiField field = entry.getKey();
        if (!field.hasModifierProperty(FINAL) || field.hasInitializer()) return false;
        if (JavaPsiRecordUtil.ILLEGAL_RECORD_COMPONENT_NAMES.contains(field.getName())) return false;
        if (entry.getValue().size() > 1) return false;
        FieldAccessorCandidate firstAccessor = ContainerUtil.getFirstItem(entry.getValue());
        if (firstAccessor == null) continue;
        if (containsObjectMethodCalls(firstAccessor.getAccessor())) return false;
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
      Arrays.stream(myClass.getFields()).filter(field -> !field.hasModifierProperty(STATIC))
        .forEach(field -> myFieldsToAccessorCandidates.put(field, new ArrayList<>()));

      for (PsiMethod method : myClass.getMethods()) {
        if (method.isConstructor()) {
          Set<PsiField> instanceFields = myFieldsToAccessorCandidates.keySet();
          myConstructorCandidates.add(new RecordConstructorCandidate(method, instanceFields));
          continue;
        }
        if (MethodUtils.isEquals(method)) {
          myEqualsMethod = method;
          continue;
        }
        if (MethodUtils.isHashCode(method)) {
          myHashCodeMethod = method;
          continue;
        }
        if (!throwsOnlyUncheckedExceptions(method)) {
          myOrdinaryMethods.add(method);
          continue;
        }
        FieldAccessorCandidate fieldAccessorCandidate = tryCreateFieldAccessorCandidate(method);
        if (fieldAccessorCandidate == null) {
          myOrdinaryMethods.add(method);
        }
        else {
          myFieldsToAccessorCandidates.putValue(fieldAccessorCandidate.myBackingField, fieldAccessorCandidate);
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
  }

  /**
   * Encapsulates information about converting of a single constructor, for example, whether it is canonical or not.
   */
  static class RecordConstructorCandidate {
    private final @NotNull PsiMethod constructorMethod;
    private final boolean canonical;
    private final @NotNull Map<PsiParameter, PsiField> ctorParamsToFields = new HashMap<>();

    private RecordConstructorCandidate(@NotNull PsiMethod constructor, @NotNull Set<PsiField> instanceFields) {
      constructorMethod = constructor;
      if (constructorMethod.getTypeParameters().length > 0) {
        canonical = false;
        return;
      }
      Set<String> instanceFieldNames = instanceFields.stream().map(PsiField::getName).collect(Collectors.toSet());
      if (instanceFieldNames.size() != instanceFields.size()) {
        canonical = false;
        return;
      }
      PsiParameter[] ctorParams = constructorMethod.getParameterList().getParameters();
      if (instanceFields.size() != ctorParams.length) {
        canonical = false;
        return;
      }
      PsiCodeBlock ctorBody = constructorMethod.getBody();
      if (ctorBody == null) {
        canonical = false;
        return;
      }

      final boolean allProcessed = PsiTreeUtil.processElements(ctorBody, PsiAssignmentExpression.class, (assignExpr) -> {
        if (!(assignExpr.getLExpression() instanceof PsiReferenceExpression leftRefExpr)) return true;
        if (!(leftRefExpr.resolve() instanceof PsiField field)) return true;

        if (!(assignExpr.getRExpression() instanceof PsiReferenceExpression rightRefExpr)) return true;
        final PsiElement assignmentValue = rightRefExpr.resolve();

        if (assignmentValue == null) return false; // using 'false' as a sentinel value
        if (!(assignmentValue instanceof PsiParameter parameter)) return true;
        ctorParamsToFields.put(parameter, field);
        return true;
      });
      if (!allProcessed) {
        canonical = false;
        return;
      }

      for (PsiField instanceField : instanceFields) {
        if (!ControlFlowUtil.variableDefinitelyAssignedIn(instanceField, ctorBody)) {
          canonical = false;
          return;
        }
      }

      canonical = true;
    }

    public @NotNull @UnmodifiableView Map<PsiParameter, PsiField> getCtorParamsToFields() {
      return Collections.unmodifiableMap(ctorParamsToFields);
    }

    @NotNull PsiMethod getConstructorMethod() {
      return constructorMethod;
    }

    @Override
    public String toString() {
      return "RecordConstructorCandidate{" +
             "constructorMethod=" + constructorMethod +
             ", canonical=" + canonical +
             ", ctorParamToFieldMap=" + ctorParamsToFields +
             '}';
    }
  }

  /**
   * Encapsulates information about converting of a single field accessor.
   * <p>
   * For instance, an existing default accessor may be removed during further record creation.
   */
  static class FieldAccessorCandidate {
    private final PsiMethod myFieldAccessor;
    private final PsiField myBackingField;
    private final boolean myDefault;
    private final boolean myRecordStyleNaming;

    private FieldAccessorCandidate(@NotNull PsiMethod accessor, @NotNull PsiField backingField, boolean recordStyleNaming) {
      myFieldAccessor = accessor;
      myBackingField = backingField;
      myRecordStyleNaming = recordStyleNaming;

      if (accessor.getDocComment() != null) {
        myDefault = false;
        return;
      }
      PsiExpression returnExpr = PropertyUtilBase.getSingleReturnValue(accessor);
      boolean isDefaultAccessor = backingField.equals(PropertyUtil.getFieldOfGetter(accessor, () -> returnExpr, false));
      if (!isDefaultAccessor) {
        myDefault = false;
        return;
      }
      myDefault = !hasAnnotationConflict(accessor, backingField, TargetType.FIELD) &&
                  !hasAnnotationConflict(backingField, accessor, TargetType.METHOD);
    }

    @NotNull PsiMethod getAccessor() {
      return myFieldAccessor;
    }

    @NotNull PsiField getBackingField() {
      return myBackingField;
    }

    boolean isDefault() {
      return myDefault;
    }

    boolean isRecordStyleNaming() {
      return myRecordStyleNaming;
    }

    @Override
    public String toString() {
      return "FieldAccessorCandidate{" +
             "myFieldAccessor=" + myFieldAccessor +
             ", myBackingField=" + myBackingField +
             '}';
    }
  }

  /**
   * During record creation we have to move the field annotations to the record component.
   * For instance, if an annotation's target includes both field and method target,
   * then we have to check whether the method is already marked by this annotation
   * as a compiler propagates annotations of the record components to appropriate targets automatically.
   */
  private static boolean hasAnnotationConflict(@NotNull PsiModifierListOwner first,
                                               @NotNull PsiModifierListOwner second,
                                               @NotNull TargetType targetType) {
    boolean result = false;
    for (PsiAnnotation firstAnn : first.getAnnotations()) {
      TargetType firstAnnTarget = AnnotationTargetUtil.findAnnotationTarget(firstAnn, targetType);
      boolean hasDesiredTarget = firstAnnTarget != null && firstAnnTarget != TargetType.UNKNOWN;
      if (!hasDesiredTarget) continue;
      if (!ContainerUtil.exists(second.getAnnotations(), secondAnn -> AnnotationUtil.equal(firstAnn, secondAnn))) {
        result = true;
        break;
      }
    }
    return result;
  }
}
