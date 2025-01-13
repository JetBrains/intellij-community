// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ClassUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.MoveAnnotationOnStaticMemberQualifyingTypeFix;
import com.intellij.codeInsight.daemon.impl.quickfix.MoveAnnotationToPackageInfoFileFix;
import com.intellij.codeInsight.daemon.impl.quickfix.MoveMembersIntoClassFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceVarWithExplicitTypeFix;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKind;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeError;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds.*;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * Fixes attached to error messages provided by {@link com.intellij.java.codeserver.highlighting.JavaErrorCollector}.
 * To add new fixes use {@link #fix(JavaErrorKind, JavaFixProvider)} or {@link #multi(JavaErrorKind, JavaFixesProvider)}
 * methods and return a fix or a list of fixes from lambda.
 */
@Service(Service.Level.APP)
final class JavaErrorFixProvider {

  private final QuickFixFactory myFactory = QuickFixFactory.getInstance();

  @FunctionalInterface
  private interface JavaFixProvider<Psi extends PsiElement, Context> {
    @Nullable CommonIntentionAction provide(@NotNull JavaCompilationError<? extends Psi, ? extends Context> error);

    default JavaFixesProvider<Psi, Context> asMulti() {
      return error -> ContainerUtil.createMaybeSingletonList(provide(error));
    }
  }

  @FunctionalInterface
  private interface JavaFixesProvider<Psi extends PsiElement, Context> {
    @NotNull List<? extends @NotNull CommonIntentionAction> provide(@NotNull JavaCompilationError<? extends Psi, ? extends Context> error);
  }

  private final Map<JavaErrorKind<?, ?>, List<JavaFixesProvider<?, ?>>> myFixes = new HashMap<>();

  JavaErrorFixProvider() {
    multi(UNSUPPORTED_FEATURE, error -> HighlightUtil.getIncreaseLanguageLevelFixes(error.psi(), error.context()));
    JavaFixProvider<PsiElement, Object> genericRemover = error -> myFactory.createDeleteFix(error.psi());
    for (JavaErrorKind<?, ?> kind : List.of(ANNOTATION_MEMBER_THROWS_NOT_ALLOWED, ANNOTATION_ATTRIBUTE_DUPLICATE,
                                            ANNOTATION_NOT_ALLOWED_EXTENDS, RECEIVER_STATIC_CONTEXT, RECEIVER_WRONG_POSITION,
                                            RECORD_HEADER_REGULAR_CLASS, INTERFACE_CLASS_INITIALIZER, INTERFACE_CONSTRUCTOR,
                                            CLASS_IMPLICIT_INITIALIZER, CLASS_IMPLICIT_PACKAGE,
                                            RECORD_EXTENDS, ENUM_EXTENDS, RECORD_PERMITS, ENUM_PERMITS, ANNOTATION_PERMITS)) {
      fix(kind, genericRemover);
    }
    
    createClassFixes();
    createExpressionFixes();
    createGenericFixes();
    createRecordFixes();
    createTypeFixes();
    createAnnotationFixes();
    createReceiverParameterFixes();
  }

  public static JavaErrorFixProvider getInstance() {
    return ApplicationManager.getApplication().getService(JavaErrorFixProvider.class);
  }

  private void createExpressionFixes() {
    fix(NEW_EXPRESSION_QUALIFIED_MALFORMED, error -> myFactory.createRemoveNewQualifierFix(error.psi(), null));
    multi(NEW_EXPRESSION_QUALIFIED_STATIC_CLASS, 
          error -> error.context().isEnum() ? List.of() : JvmElementActionFactories.createModifierActions(
            error.context(), MemberRequestsKt.modifierRequest(JvmModifier.STATIC, false)));
    fix(NEW_EXPRESSION_QUALIFIED_STATIC_CLASS, error -> myFactory.createRemoveNewQualifierFix(error.psi(), error.context()));
    fix(NEW_EXPRESSION_QUALIFIED_ANONYMOUS_IMPLEMENTS_INTERFACE, error -> myFactory.createRemoveNewQualifierFix(error.psi(), null));
    fix(NEW_EXPRESSION_QUALIFIED_QUALIFIED_CLASS_REFERENCE,
        error -> myFactory.createDeleteFix(error.psi(), QuickFixBundle.message("remove.qualifier.fix")));
    fix(LITERAL_CHARACTER_TOO_LONG, error -> myFactory.createConvertToStringLiteralAction());
    fix(LITERAL_CHARACTER_EMPTY, error -> myFactory.createConvertToStringLiteralAction());
  }

  private void createTypeFixes() {
    multi(TYPE_INCOMPATIBLE, error -> {
      JavaIncompatibleTypeError context = error.context();
      PsiElement anchor = error.psi();
      PsiElement parent = anchor.getParent();
      if (anchor instanceof PsiJavaCodeReferenceElement && parent instanceof PsiReferenceList &&
          parent.getParent() instanceof PsiMethod method && method.getThrowsList() == parent) {
        // Incompatible type in throws clause
        PsiClass usedClass = PsiUtil.resolveClassInClassTypeOnly(context.rType());
        if (usedClass != null && context.lType() instanceof PsiClassType throwableType) {
          return List.of(myFactory.createExtendsListFix(usedClass, throwableType, true));
        }
      }
      return List.of();
    });

  }

  private void createGenericFixes() {
    fix(TYPE_PARAMETER_EXTENDS_INTERFACE_EXPECTED, error -> {
      PsiClassType type = JavaPsiFacade.getElementFactory(error.project()).createType(error.psi());
      return myFactory.createMoveBoundClassToFrontFix(error.context(), type);
    });
    fix(TYPE_PARAMETER_CANNOT_BE_FOLLOWED_BY_OTHER_BOUNDS, error -> {
      PsiClassType type = JavaPsiFacade.getElementFactory(error.project()).createType(error.psi());
      return myFactory.createExtendsListFix(error.context(), type, false);
    });
  }

  private void createClassFixes() {
    fix(CLASS_NO_ABSTRACT_METHOD, error -> {
      if (error.psi() instanceof PsiClass aClass && !(aClass instanceof PsiAnonymousClass) && !aClass.isEnum()
          && aClass.getModifierList() != null
          && HighlightUtil.getIncompatibleModifier(PsiModifier.ABSTRACT, aClass.getModifierList()) == null) {
        return addModifierFix(aClass, PsiModifier.ABSTRACT);
      }
      return null;
    });
    multi(CLASS_NO_ABSTRACT_METHOD, error -> {
      PsiMember member = error.psi();
      PsiClass aClass = member instanceof PsiEnumConstant enumConstant ?
                        requireNonNullElse(enumConstant.getInitializingClass(), member.getContainingClass()) : (PsiClass)member;
      PsiClass containingClass = requireNonNull(error.context().getContainingClass());
      PsiMethod anyMethodToImplement = member instanceof PsiEnumConstant ? ClassUtil.getAnyAbstractMethod(aClass) : 
                                       ClassUtil.getAnyMethodToImplement(aClass);
      if (anyMethodToImplement == null) return List.of();
      if (!anyMethodToImplement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) ||
          JavaPsiFacade.getInstance(error.project()).arePackagesTheSame(aClass, containingClass)) {
        return List.of(myFactory.createImplementMethodsFix(member));
      }
      else {
        return StreamEx.of(JvmModifier.PROTECTED, JvmModifier.PUBLIC)
          .flatCollection(modifier ->
          JvmElementActionFactories.createModifierActions(anyMethodToImplement, MemberRequestsKt.modifierRequest(modifier, true)))
          .toList();
      }
    });
    fix(CLASS_REFERENCE_LIST_DUPLICATE,
        error -> myFactory.createRemoveDuplicateExtendsAction(HighlightUtil.formatClass(error.context())));
    multi(CLASS_REFERENCE_LIST_INNER_PRIVATE, error -> List.of(
      addModifierFix(error.context(), PsiModifier.PUBLIC),
      addModifierFix(error.context(), PsiModifier.PROTECTED)));
    fix(CLASS_DUPLICATE, error -> myFactory.createRenameFix(requireNonNullElse(error.psi().getNameIdentifier(), error.psi())));
    fix(CLASS_CLASHES_WITH_PACKAGE, error -> myFactory.createRenameFix(requireNonNullElse(error.psi().getNameIdentifier(), error.psi())));
    fix(CLASS_DUPLICATE, error -> myFactory.createNavigateToDuplicateElementFix(error.context()));
    fix(CLASS_DUPLICATE_IN_OTHER_FILE, error -> myFactory.createNavigateToDuplicateElementFix(error.context()));
    multi(INSTANTIATION_ABSTRACT, error -> {
      PsiClass aClass = error.context();
      PsiMethod anyAbstractMethod = ClassUtil.getAnyAbstractMethod(aClass);
      List<CommonIntentionAction> registrar = new ArrayList<>();
      if (!aClass.isInterface() && anyAbstractMethod == null) {
        registrar.addAll(JvmElementActionFactories.createModifierActions(aClass, MemberRequestsKt.modifierRequest(
          JvmModifier.ABSTRACT, false)));
      }
      if (anyAbstractMethod != null && error.psi() instanceof PsiNewExpression newExpression && newExpression.getClassReference() != null) {
        registrar.add(myFactory.createImplementAbstractClassMethodsFix(newExpression));
      }
      return registrar;
    });
    multi(CLASS_WRONG_FILE_NAME, error -> {
      PsiClass aClass = error.psi();
      PsiJavaFile file = (PsiJavaFile)aClass.getContainingFile();
      PsiClass[] classes = file.getClasses();
      boolean containsClassForFile = ContainerUtil.exists(classes, otherClass ->
        !otherClass.getManager().areElementsEquivalent(otherClass, aClass) &&
        otherClass.hasModifierProperty(PsiModifier.PUBLIC) &&
        file.getVirtualFile().getNameWithoutExtension().equals(otherClass.getName()));
      List<CommonIntentionAction> registrar = new ArrayList<>();
      if (!containsClassForFile) {
        registrar.add(myFactory.createRenameFileFix(aClass.getName() + JavaFileType.DOT_DEFAULT_EXTENSION));
      }
      if (classes.length > 1) {
        registrar.add(myFactory.createMoveClassToSeparateFileFix(aClass));
      }
      registrar.add(myFactory.createModifierListFix(aClass, PsiModifier.PUBLIC, false, false));
      if (!containsClassForFile) {
        registrar.add(myFactory.createRenameElementFix(aClass));
      }
      return registrar;
    });
    fix(CLASS_SEALED_INCOMPLETE_PERMITS, error -> myFactory.createFillPermitsListFix(requireNonNull(error.psi().getNameIdentifier())));
    multi(CLASS_SEALED_INHERITOR_EXPECTED_MODIFIERS_CAN_BE_FINAL, error -> List.of(
      addModifierFix(error.psi(), PsiModifier.FINAL),
      addModifierFix(error.psi(), PsiModifier.SEALED),
      addModifierFix(error.psi(), PsiModifier.NON_SEALED)));
    multi(CLASS_SEALED_INHERITOR_EXPECTED_MODIFIERS, error -> List.of(
      addModifierFix(error.psi(), PsiModifier.SEALED),
      addModifierFix(error.psi(), PsiModifier.NON_SEALED)));
    fix(CLASS_IMPLICIT_NO_MAIN_METHOD, error -> myFactory.createAddMainMethodFix(error.context()));
    fix(UNSUPPORTED_FEATURE, error -> {
      if (error.context() != JavaFeature.IMPLICIT_CLASSES) return null;
      PsiMember member = PsiTreeUtil.getNonStrictParentOfType(error.psi(), PsiMember.class);
      if (member == null || member instanceof PsiClass) return null;
      if (!(member.getContainingClass() instanceof PsiImplicitClass implicitClass)) return null;
      boolean hasClassToRelocate = PsiTreeUtil.findChildOfType(implicitClass, PsiClass.class) != null;
      return hasClassToRelocate ? new MoveMembersIntoClassFix(implicitClass) : null;
    });
    multi(UNSUPPORTED_FEATURE, error -> {
      if (error.context() != JavaFeature.INNER_STATICS) return List.of();
      PsiMember member = PsiTreeUtil.getParentOfType(error.psi(), PsiMember.class);
      if (member == null) return List.of();
      List<CommonIntentionAction> registrar = new ArrayList<>();
      if (PsiUtil.isJavaToken(error.psi(), JavaTokenType.STATIC_KEYWORD)) {
        registrar.add(myFactory.createModifierListFix(member, PsiModifier.STATIC, false, false));
      }
      PsiClass containingClass = member.getContainingClass();
      if (containingClass != null && containingClass.getContainingClass() != null) {
        registrar.add(addModifierFix(containingClass, PsiModifier.STATIC));
      }
      return registrar;
    });
    fix(INTERFACE_CONSTRUCTOR, error -> myFactory.createConvertInterfaceToClassFix(requireNonNull(error.psi().getContainingClass())));
    fix(INTERFACE_CLASS_INITIALIZER, error -> myFactory.createConvertInterfaceToClassFix(requireNonNull(error.psi().getContainingClass())));
    fix(INTERFACE_IMPLEMENTS, error -> {
      PsiClassType[] referencedTypes = error.psi().getReferencedTypes();
      if (referencedTypes.length > 0 && error.psi().getParent() instanceof PsiClass aClass) {
        return myFactory.createChangeExtendsToImplementsFix(aClass, referencedTypes[0]);
      }
      return null;
    });
    JavaFixProvider<PsiJavaCodeReferenceElement, PsiClass> extendsToImplementsFix = error -> {
      PsiJavaCodeReferenceElement ref = error.psi();
      PsiClassType type = JavaPsiFacade.getElementFactory(error.project()).createType(ref);
      return myFactory.createChangeExtendsToImplementsFix(error.context(), type);
    };
    fix(CLASS_EXTENDS_INTERFACE, extendsToImplementsFix);
    fix(CLASS_IMPLEMENTS_CLASS, extendsToImplementsFix);
    fix(INTERFACE_EXTENDS_CLASS, extendsToImplementsFix);
    fix(CLASS_SEALED_PERMITS_ON_NON_SEALED, error -> addModifierFix(error.psi(), PsiModifier.SEALED));
    multi(CLASS_EXTENDS_FINAL, error ->
      JvmElementActionFactories.createModifierActions(error.context(), MemberRequestsKt.modifierRequest(JvmModifier.FINAL, false)));
    fix(CLASS_ANONYMOUS_EXTENDS_SEALED, error -> myFactory.createConvertAnonymousToInnerAction(error.psi()));
  }
  
  private void createRecordFixes() {
    fix(RECORD_NO_HEADER, error -> myFactory.createAddEmptyRecordHeaderFix(error.psi()));
    fix(RECORD_INSTANCE_FIELD, error -> addModifierFix(error.psi(), PsiModifier.STATIC));
    fix(RECORD_INSTANCE_INITIALIZER, error -> addModifierFix(error.psi(), PsiModifier.STATIC));
  }

  private void createReceiverParameterFixes() {
    fix(RECEIVER_TYPE_MISMATCH, error -> myFactory.createReceiverParameterTypeFix(error.psi(), error.context()));
    fix(RECEIVER_NAME_MISMATCH,
        error -> error.context() == null ? null : myFactory.createReceiverParameterNameFix(error.psi(), error.context()));
    fix(RECEIVER_STATIC_CONTEXT,
           error -> error.psi().getParent().getParent() instanceof PsiMethod method ?
                    myFactory.createModifierListFix(method.getModifierList(), PsiModifier.STATIC, false, false) : null);
    fix(RECEIVER_WRONG_POSITION, error -> {
      if (error.psi().getParent().getParent() instanceof PsiMethod method) {
        PsiReceiverParameter firstReceiverParameter = PsiTreeUtil.getChildOfType(method.getParameterList(), PsiReceiverParameter.class);
        if (!PsiUtil.isJavaToken(PsiTreeUtil.skipWhitespacesAndCommentsBackward(firstReceiverParameter), JavaTokenType.LPARENTH)) {
          return myFactory.createMakeReceiverParameterFirstFix(error.psi());
        }
      }
      return null;
    });
  }

  private void createAnnotationFixes() {
    fix(SAFE_VARARGS_ON_NON_FINAL_METHOD,
        error -> myFactory.createModifierListFix(error.context(), PsiModifier.FINAL, true, true));
    multi(OVERRIDE_ON_NON_OVERRIDING_METHOD, error -> {
      List<CommonIntentionAction> registrar = new ArrayList<>();
      myFactory.registerPullAsAbstractUpFixes(error.context(), registrar);
      return registrar;
    });
    JavaFixProvider<PsiElement, Object> annotationRemover = error ->
      error.psi() instanceof PsiAnnotation annotation ? myFactory.createDeleteFix(annotation, JavaAnalysisBundle.message(
        "remove.annotation")) : null;
    for (JavaErrorKind<?, ?> kind : List.of(ANNOTATION_NOT_ALLOWED_CLASS, ANNOTATION_NOT_ALLOWED_HERE,
                                            ANNOTATION_NOT_ALLOWED_REF, ANNOTATION_NOT_ALLOWED_VAR,
                                            ANNOTATION_NOT_ALLOWED_VOID, LAMBDA_MULTIPLE_TARGET_METHODS, LAMBDA_NO_TARGET_METHOD,
                                            LAMBDA_NOT_FUNCTIONAL_INTERFACE, ANNOTATION_NOT_APPLICABLE,
                                            LAMBDA_FUNCTIONAL_INTERFACE_SEALED, OVERRIDE_ON_STATIC_METHOD,
                                            OVERRIDE_ON_NON_OVERRIDING_METHOD, SAFE_VARARGS_ON_FIXED_ARITY,
                                            SAFE_VARARGS_ON_NON_FINAL_METHOD, SAFE_VARARGS_ON_RECORD_COMPONENT,
                                            ANNOTATION_CONTAINER_WRONG_PLACE, ANNOTATION_CONTAINER_NOT_APPLICABLE)) {
      fix(kind, annotationRemover);
    }
    fix(ANNOTATION_NOT_ALLOWED_VAR, error -> {
      PsiAnnotationOwner owner = error.psi().getOwner();
      PsiTypeElement type = owner instanceof PsiTypeElement te ? te :
                            PsiTreeUtil.skipSiblingsForward((PsiModifierList)owner, PsiComment.class, PsiWhiteSpace.class,
                                                            PsiTypeParameterList.class) instanceof PsiTypeElement te ? te :
                            null;
      return type != null && type.isInferredType() ? new ReplaceVarWithExplicitTypeFix(type) : null;
    });
    multi(ANNOTATION_NOT_APPLICABLE, error -> {
      if (!BaseIntentionAction.canModify(requireNonNull(error.psi().resolveAnnotationType()))) return List.of();
      return ContainerUtil.map(error.context(), targetType -> myFactory.createAddAnnotationTargetFix(error.psi(), targetType));
    });
    fix(ANNOTATION_NOT_ALLOWED_STATIC, error -> new MoveAnnotationOnStaticMemberQualifyingTypeFix(error.psi()));
    fix(ANNOTATION_MISSING_ATTRIBUTE, error -> myFactory.createAddMissingRequiredAnnotationParametersFix(
      error.psi(), PsiMethod.EMPTY_ARRAY, error.context()));
    multi(ANNOTATION_ATTRIBUTE_ANNOTATION_NAME_IS_MISSING, error -> myFactory.createAddAnnotationAttributeNameFixes(error.psi()));
    multi(ANNOTATION_ATTRIBUTE_UNKNOWN_METHOD, error -> {
      PsiNameValuePair pair = error.psi();
      if (pair.getName() != null) return List.of();
      return myFactory.createAddAnnotationAttributeNameFixes(pair);
    });
    fix(ANNOTATION_ATTRIBUTE_UNKNOWN_METHOD, error -> myFactory.createCreateAnnotationMethodFromUsageFix(error.psi()));
    fix(ANNOTATION_ATTRIBUTE_DUPLICATE, error -> myFactory.createMergeDuplicateAttributesFix(error.psi()));
    JavaFixProvider<PsiAnnotationMemberValue, AnnotationValueErrorContext> incompatibleTypeFix = error -> {
      PsiAnnotationMemberValue value = error.psi();
      PsiAnnotationMethod method = error.context().method();
      PsiType type = null;
      if (value instanceof PsiAnnotation annotation) {
        PsiClass annotationClass = annotation.resolveAnnotationType();
        if (annotationClass != null) {
          type = TypeUtils.getType(annotationClass);
        }
      } else if (value instanceof PsiArrayInitializerMemberValue arrayInitializer) {
        PsiAnnotationMemberValue[] initializers = arrayInitializer.getInitializers();
        PsiType componentType = initializers.length == 0 ? error.context().expectedType() :
                                initializers[0] instanceof PsiExpression firstInitializer ? firstInitializer.getType() : null;
        if (componentType != null) {
          type = componentType.createArrayType();
        }
      } else if (value instanceof PsiExpression expression) {
        type = expression.getType();
      }
      if (type == null) return null;
      return myFactory.createAnnotationMethodReturnFix(method, type, error.context().fromDefaultValue());
    };
    fix(ANNOTATION_ATTRIBUTE_INCOMPATIBLE_TYPE, incompatibleTypeFix);
    fix(ANNOTATION_ATTRIBUTE_INCOMPATIBLE_TYPE,
        error -> myFactory.createSurroundWithQuotesAnnotationParameterValueFix(error.psi(), error.context().expectedType()));
    fix(ANNOTATION_ATTRIBUTE_ILLEGAL_ARRAY_INITIALIZER, incompatibleTypeFix);
    fix(ANNOTATION_ATTRIBUTE_ILLEGAL_ARRAY_INITIALIZER, error -> {
      PsiAnnotationMemberValue[] initializers = error.psi().getInitializers();
      if (initializers.length != 1 || !(initializers[0] instanceof PsiExpression firstInitializer)) return null;
      PsiType expectedType = error.context().expectedType();
      if (!TypeConversionUtil.areTypesAssignmentCompatible(expectedType, firstInitializer)) return null;
      return myFactory.createUnwrapArrayInitializerMemberValueAction(error.psi());
    });
    multi(ANNOTATION_NOT_ALLOWED_ON_PACKAGE, error ->
      List.of(myFactory.createDeleteFix(requireNonNull(error.psi().getAnnotationList()),
                                        JavaAnalysisBundle.message("intention.text.remove.annotation")),
              new MoveAnnotationToPackageInfoFileFix(error.psi())));
    fix(ANNOTATION_NOT_ALLOWED_ON_PACKAGE, error ->
      myFactory.createDeleteFix(error.psi(), JavaAnalysisBundle.message("intention.text.remove.annotation")));
    fix(ANNOTATION_DUPLICATE_NON_REPEATABLE, error -> myFactory.createCollapseAnnotationsFix(error.psi()));
  }

  private @NotNull CommonIntentionAction addModifierFix(PsiModifierListOwner owner, @PsiModifier.ModifierConstant String modifier) {
    return myFactory.createModifierListFix(owner, modifier, true, false);
  }

  private <Psi extends PsiElement, Context> void fix(@NotNull JavaErrorKind<Psi, Context> kind,
                                                     @NotNull JavaFixProvider<? super Psi, ? super Context> fixProvider) {
    multi(kind, fixProvider.asMulti());
  }

  private <Psi extends PsiElement, Context> void multi(@NotNull JavaErrorKind<Psi, Context> kind,
                                                       @NotNull JavaFixesProvider<? super Psi, ? super Context> fixProvider) {
    myFixes.computeIfAbsent(kind, k -> new ArrayList<>()).add(fixProvider);
  }

  @NotNull List<CommonIntentionAction> getFixes(@NotNull JavaCompilationError<?, ?> error) {
    var providers = myFixes.get(error.kind());
    if (providers == null) return List.of();
    List<CommonIntentionAction> fixes = new ArrayList<>();
    for (var provider : providers) {
      @SuppressWarnings("unchecked") var fn = (JavaFixesProvider<PsiElement, Object>)provider;
      fixes.addAll(fn.provide(error));
    }
    return fixes;
  }
}
