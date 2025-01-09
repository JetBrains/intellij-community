// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.quickfix.MoveAnnotationOnStaticMemberQualifyingTypeFix;
import com.intellij.codeInsight.daemon.impl.quickfix.MoveAnnotationToPackageInfoFileFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceVarWithExplicitTypeFix;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.codeserver.highlighting.errors.JavaAnnotationValueErrorKind;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKind;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds.*;

/**
 * Fixes attached to error messages provided by {@link com.intellij.java.codeserver.highlighting.JavaErrorCollector}.
 * To add new fixes use {@link #single(JavaErrorKind, JavaFixProvider)} or {@link #multi(JavaErrorKind, JavaFixesProvider)}
 * methods and return a fix or a list of fixes from lambda.
 */
final class JavaErrorFixProvider {
  @FunctionalInterface
  private interface JavaFixProvider<Psi extends PsiElement, Context> {
    @Nullable CommonIntentionAction provide(@NotNull JavaCompilationError<? extends Psi, ? extends Context> error);

    default JavaFixesProvider<Psi, Context> asMulti() {
      return error -> ContainerUtil.createMaybeSingletonList(provide(error));
    }
  }

  @FunctionalInterface
  private interface JavaFixesProvider<Psi extends PsiElement, Context> {
    @NotNull List<@NotNull CommonIntentionAction> provide(@NotNull JavaCompilationError<? extends Psi, ? extends Context> error);
  }

  private static final Map<JavaErrorKind<?, ?>, List<JavaFixesProvider<?, ?>>> FIXES = new HashMap<>();

  static {
    QuickFixFactory factory = QuickFixFactory.getInstance();
    multi(UNSUPPORTED_FEATURE, error -> HighlightUtil.getIncreaseLanguageLevelFixes(error.psi(), error.context()));
    JavaFixProvider<PsiElement, Object> genericRemover = error -> factory.createDeleteFix(error.psi());
    for (JavaErrorKind<?, ?> kind : List.of(ANNOTATION_MEMBER_THROWS_NOT_ALLOWED, ANNOTATION_ATTRIBUTE_DUPLICATE,
                                            ANNOTATION_NOT_ALLOWED_EXTENDS, RECEIVER_STATIC_CONTEXT, RECEIVER_WRONG_POSITION)) {
      single(kind, genericRemover);
    }
    
    createAnnotationFixes(factory);
    createReceiverParameterFixes(factory);
  }

  private static void createReceiverParameterFixes(@NotNull QuickFixFactory factory) {
    single(RECEIVER_TYPE_MISMATCH, error -> factory.createReceiverParameterTypeFix(error.psi(), error.context()));
    single(RECEIVER_NAME_MISMATCH,
           error -> error.context() == null ? null : factory.createReceiverParameterNameFix(error.psi(), error.context()));
    single(RECEIVER_STATIC_CONTEXT,
           error -> error.psi().getParent().getParent() instanceof PsiMethod method ?
                    factory.createModifierListFix(method.getModifierList(), PsiModifier.STATIC, false, false) : null);
    single(RECEIVER_WRONG_POSITION, error -> {
      if (error.psi().getParent().getParent() instanceof PsiMethod method) {
        PsiReceiverParameter firstReceiverParameter = PsiTreeUtil.getChildOfType(method.getParameterList(), PsiReceiverParameter.class);
        if (!PsiUtil.isJavaToken(PsiTreeUtil.skipWhitespacesAndCommentsBackward(firstReceiverParameter), JavaTokenType.LPARENTH)) {
          return factory.createMakeReceiverParameterFirstFix(error.psi());
        }
      }
      return null;
    });
  }

  private static void createAnnotationFixes(@NotNull QuickFixFactory factory) {
    single(SAFE_VARARGS_ON_NON_FINAL_METHOD,
           error -> factory.createModifierListFix(error.context(), PsiModifier.FINAL, true, true));
    multi(OVERRIDE_ON_NON_OVERRIDING_METHOD, error -> {
      List<CommonIntentionAction> registrar = new ArrayList<>();
      factory.registerPullAsAbstractUpFixes(error.context(), registrar);
      return registrar;
    });
    JavaFixProvider<PsiElement, Object> annotationRemover = error ->
      error.psi() instanceof PsiAnnotation annotation ? factory.createDeleteFix(annotation, JavaAnalysisBundle.message(
        "remove.annotation")) : null;
    for (JavaErrorKind<?, ?> kind : List.of(ANNOTATION_NOT_ALLOWED_CLASS, ANNOTATION_NOT_ALLOWED_HERE,
                                            ANNOTATION_NOT_ALLOWED_REF, ANNOTATION_NOT_ALLOWED_VAR,
                                            ANNOTATION_NOT_ALLOWED_VOID, LAMBDA_MULTIPLE_TARGET_METHODS, LAMBDA_NO_TARGET_METHOD,
                                            LAMBDA_NOT_FUNCTIONAL_INTERFACE, ANNOTATION_NOT_APPLICABLE,
                                            LAMBDA_FUNCTIONAL_INTERFACE_SEALED, OVERRIDE_ON_STATIC_METHOD,
                                            OVERRIDE_ON_NON_OVERRIDING_METHOD, SAFE_VARARGS_ON_FIXED_ARITY,
                                            SAFE_VARARGS_ON_NON_FINAL_METHOD, SAFE_VARARGS_ON_RECORD_COMPONENT)) {
      single(kind, annotationRemover);
    }
    single(ANNOTATION_NOT_ALLOWED_VAR, error -> {
      PsiAnnotationOwner owner = error.psi().getOwner();
      PsiTypeElement type = owner instanceof PsiTypeElement te ? te :
                            PsiTreeUtil.skipSiblingsForward((PsiModifierList)owner, PsiComment.class, PsiWhiteSpace.class,
                                                            PsiTypeParameterList.class) instanceof PsiTypeElement te ? te :
                            null;
      return type != null && type.isInferredType() ? new ReplaceVarWithExplicitTypeFix(type) : null;
    });
    multi(ANNOTATION_NOT_APPLICABLE, error -> {
      if (!BaseIntentionAction.canModify(Objects.requireNonNull(error.psi().resolveAnnotationType()))) return List.of();
      return ContainerUtil.map(error.context(),
                               targetType -> factory.createAddAnnotationTargetFix(error.psi(), targetType));
    });
    single(ANNOTATION_NOT_ALLOWED_STATIC, error -> new MoveAnnotationOnStaticMemberQualifyingTypeFix(error.psi()));
    single(ANNOTATION_MISSING_ATTRIBUTE, error -> factory.createAddMissingRequiredAnnotationParametersFix(
      error.psi(), PsiMethod.EMPTY_ARRAY, error.context()));
    multi(ANNOTATION_ATTRIBUTE_ANNOTATION_NAME_IS_MISSING,
          error -> List.copyOf(factory.createAddAnnotationAttributeNameFixes(error.psi())));
    multi(ANNOTATION_ATTRIBUTE_UNKNOWN_METHOD, error -> {
      PsiNameValuePair pair = error.psi();
      if (pair.getName() != null) return List.of();
      return List.copyOf(factory.createAddAnnotationAttributeNameFixes(pair));
    });
    single(ANNOTATION_ATTRIBUTE_UNKNOWN_METHOD, error -> factory.createCreateAnnotationMethodFromUsageFix(error.psi()));
    single(ANNOTATION_ATTRIBUTE_DUPLICATE, error -> factory.createMergeDuplicateAttributesFix(error.psi()));
    JavaFixProvider<PsiAnnotationMemberValue, JavaAnnotationValueErrorKind.AnnotationValueErrorContext> incompatibleTypeFix = error -> {
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
      return factory.createAnnotationMethodReturnFix(method, type, error.context().fromDefaultValue());
    };
    single(ANNOTATION_ATTRIBUTE_INCOMPATIBLE_TYPE, incompatibleTypeFix);
    single(ANNOTATION_ATTRIBUTE_INCOMPATIBLE_TYPE, 
           error -> factory.createSurroundWithQuotesAnnotationParameterValueFix(error.psi(), error.context().expectedType()));
    single(ANNOTATION_ATTRIBUTE_ILLEGAL_ARRAY_INITIALIZER, incompatibleTypeFix);
    single(ANNOTATION_ATTRIBUTE_ILLEGAL_ARRAY_INITIALIZER, error -> {
      PsiAnnotationMemberValue[] initializers = error.psi().getInitializers();
      if (initializers.length != 1 || !(initializers[0] instanceof PsiExpression firstInitializer)) return null;
      PsiType expectedType = error.context().expectedType();
      if (!TypeConversionUtil.areTypesAssignmentCompatible(expectedType, firstInitializer)) return null;
      return factory.createUnwrapArrayInitializerMemberValueAction(error.psi());
    });
    multi(ANNOTATION_NOT_ALLOWED_ON_PACKAGE, error ->
      List.of(factory.createDeleteFix(Objects.requireNonNull(error.psi().getAnnotationList()), 
                                      JavaAnalysisBundle.message("intention.text.remove.annotation")),
              new MoveAnnotationToPackageInfoFileFix(error.psi())));
    single(ANNOTATION_NOT_ALLOWED_ON_PACKAGE, error ->
      factory.createDeleteFix(error.psi(), JavaAnalysisBundle.message("intention.text.remove.annotation")));
  }

  private static <Psi extends PsiElement, Context> void single(@NotNull JavaErrorKind<Psi, Context> kind,
                                                               @NotNull JavaFixProvider<? super Psi, ? super Context> fixProvider) {
    multi(kind, fixProvider.asMulti());
  }

  private static <Psi extends PsiElement, Context> void multi(@NotNull JavaErrorKind<Psi, Context> kind,
                                                              @NotNull JavaFixesProvider<? super Psi, ? super Context> fixProvider) {
    FIXES.computeIfAbsent(kind, k -> new ArrayList<>()).add(fixProvider);
  }

  static @NotNull List<CommonIntentionAction> getFixes(@NotNull JavaCompilationError<?, ?> error) {
    var providers = FIXES.get(error.kind());
    if (providers == null) return List.of();
    List<CommonIntentionAction> fixes = new ArrayList<>();
    for (var provider : providers) {
      @SuppressWarnings("unchecked") var fn = (JavaFixesProvider<PsiElement, Object>)provider;
      fixes.addAll(fn.provide(error));
    }
    return fixes;
  }
}
