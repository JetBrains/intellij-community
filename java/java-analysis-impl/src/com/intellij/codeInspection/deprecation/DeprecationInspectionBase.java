// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deprecation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptCheckbox;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

public abstract class DeprecationInspectionBase extends LocalInspectionTool {
  public boolean IGNORE_IN_SAME_OUTERMOST_CLASS = true;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  public static void checkDeprecated(@NotNull PsiModifierListOwner element,
                                     @NotNull PsiElement elementToHighlight,
                                     @Nullable TextRange rangeInElement,
                                     boolean ignoreInsideDeprecated,
                                     boolean ignoreImportStatements,
                                     boolean ignoreMethodsOfDeprecated,
                                     boolean ignoreInSameOutermostClass,
                                     @NotNull ProblemsHolder holder,
                                     boolean forRemoval) {
    if (PsiImplUtil.isDeprecated(element)) {
      if (forRemoval != isForRemovalAttributeSet(element)) {
        return;
      }
    }
    else {
      if (!ignoreMethodsOfDeprecated) {
        PsiClass containingClass = element instanceof PsiMember ? ((PsiMember)element).getContainingClass() : null;
        if (containingClass != null) {
          checkDeprecated(containingClass, elementToHighlight, rangeInElement, ignoreInsideDeprecated, ignoreImportStatements,
                          false, ignoreInSameOutermostClass, holder, forRemoval);
        }
      }
      return;
    }

    if (ignoreInSameOutermostClass && areElementsInSameOutermostClass(element, elementToHighlight)) return;

    if (ignoreInsideDeprecated && isElementInsideDeprecated(elementToHighlight)) return;

    if (ignoreImportStatements && isElementInsideImportStatement(elementToHighlight)) return;

    String description = JavaErrorBundle.message(forRemoval ? "marked.for.removal.symbol" : "deprecated.symbol",
                                                 getPresentableName(element));

    LocalQuickFix replacementQuickFix = getReplacementQuickFix(element, elementToHighlight);

    holder.registerProblem(elementToHighlight, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, rangeInElement,
                           LocalQuickFix.notNullElements(replacementQuickFix));
  }

  private static boolean isElementInsideImportStatement(@NotNull PsiElement elementToHighlight) {
    return PsiTreeUtil.getParentOfType(elementToHighlight, PsiImportStatement.class) != null;
  }

  public static boolean isElementInsideDeprecated(@NotNull PsiElement element) {
    PsiElement parent = element;
    while ((parent = PsiTreeUtil.getParentOfType(parent, PsiModifierListOwner.class, true)) != null) {
      if (PsiImplUtil.isDeprecated(parent)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static LocalQuickFix getReplacementQuickFix(@NotNull PsiModifierListOwner deprecatedElement, 
                                                      @NotNull PsiElement elementToHighlight) {
    PsiMethodCallExpression methodCall = getMethodCall(elementToHighlight);
    if (deprecatedElement instanceof PsiMethod method && methodCall != null) {
      PsiMethod replacement = findReplacementInJavaDoc(method, methodCall);
      if (replacement != null) {
        return new ReplaceMethodCallFix((PsiMethodCallExpression)elementToHighlight.getParent().getParent(), replacement);
      }
    }
    if (deprecatedElement instanceof PsiField field) {
      PsiReferenceExpression referenceExpression = getFieldReferenceExpression(elementToHighlight);
      if (referenceExpression != null) {
        PsiMember replacement = findReplacementInJavaDoc(field, referenceExpression);
        if (replacement != null) {
          return new ReplaceFieldReferenceFix(referenceExpression, replacement);
        }
      }
    }
    return null;
  }

  public static String getPresentableName(@NotNull PsiElement psiElement) {
    //Annotation attribute methods don't have parameters.
    if (psiElement instanceof PsiMethod && PsiUtil.isAnnotationMethod(psiElement)) {
      return ((PsiMethod)psiElement).getName();
    }
    return HighlightMessageUtil.getSymbolName(psiElement);
  }

  protected static boolean isForRemovalAttributeSet(@NotNull PsiModifierListOwner element) {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(element, CommonClassNames.JAVA_LANG_DEPRECATED);
    if (annotation != null) {
      return isForRemovalAttributeSet(annotation);
    }
    return false;
  }

  /**
   * Returns value of {@link Deprecated#forRemoval} attribute, which is available since Java 9.
   *
   * @param deprecatedAnnotation annotation instance to extract value of
   * @return {@code true} if the {@code forRemoval} attribute is set to true,
   * {@code false} if it isn't set or is set to {@code false}.
   */
  protected static boolean isForRemovalAttributeSet(@NotNull PsiAnnotation deprecatedAnnotation) {
    return Boolean.TRUE == AnnotationUtil.getBooleanAttributeValue(deprecatedAnnotation, "forRemoval");
  }

  private static boolean areElementsInSameOutermostClass(PsiElement refElement, PsiElement elementToHighlight) {
    PsiClass outermostClass = CachedValuesManager.getCachedValue(
      refElement,
      () -> new CachedValueProvider.Result<>(PsiUtil.getTopLevelClass(refElement), PsiModificationTracker.MODIFICATION_COUNT)
    );
    return outermostClass != null && outermostClass == PsiUtil.getTopLevelClass(elementToHighlight);
  }

  static OptCheckbox getSameOutermostClassCheckBox() {
    return OptPane.checkbox("IGNORE_IN_SAME_OUTERMOST_CLASS", JavaAnalysisBundle.message("ignore.in.the.same.outermost.class"));
  }

  private static PsiMember findReplacementInJavaDoc(@NotNull PsiField field, @NotNull PsiReferenceExpression referenceExpression) {
    PsiClass qualifierClass = RefactoringChangeUtil.getQualifierClass(referenceExpression);
    return getReplacementCandidatesFromJavadoc(field, PsiField.class, field, qualifierClass)
      .filter(tagField -> areReplaceable(tagField, referenceExpression))
      .select(PsiMember.class)
      .append(getReplacementCandidatesFromJavadoc(field, PsiMethod.class, field, qualifierClass)
                .filter(tagMethod -> areReplaceable(tagMethod, referenceExpression)))
      .collect(MoreCollectors.onlyOne())
      .orElse(null);
  }

  private static PsiMethod findReplacementInJavaDoc(@NotNull PsiMethod method, @NotNull PsiMethodCallExpression call) {
    if (method instanceof PsiConstructorCall) return null;
    if (method instanceof ClsMethodImpl) {
      PsiMethod sourceMethod = ((ClsMethodImpl)method).getSourceMirrorMethod();
      return sourceMethod == null ? null : findReplacementInJavaDoc(sourceMethod, call);
    }

    return getReplacementCandidatesFromJavadoc(method, PsiMethod.class, call,
                                               RefactoringChangeUtil.getQualifierClass(call.getMethodExpression()))
      .filter(tagMethod -> areReplaceable(method, tagMethod, call))
      .collect(MoreCollectors.onlyOne())
      .orElse(null);
  }

  @NotNull
  private static <T extends PsiDocCommentOwner> StreamEx<? extends T> getReplacementCandidatesFromJavadoc(PsiDocCommentOwner member, Class<T> clazz, PsiElement context, PsiClass qualifierClass) {
    PsiDocComment doc = member.getDocComment();
    if (doc == null) return StreamEx.empty();

    Collection<PsiDocTag> docTags = PsiTreeUtil.findChildrenOfType(doc, PsiDocTag.class);
    if (docTags.isEmpty()) return StreamEx.empty();
    return StreamEx.of(docTags)
      .filter(t -> {
        String name = t.getName();
        return "link".equals(name) || "see".equals(name);
      })
      .map(tag -> tag.getValueElement())
      .nonNull()
      .map(value -> value.getReference())
      .nonNull()
      .map(reference -> reference.resolve())
      .select(clazz)
      .distinct()
      .filter(tagMethod -> !tagMethod.isDeprecated()) // not deprecated
      .filter(tagMethod -> PsiResolveHelper.getInstance(context.getProject()).isAccessible(tagMethod, context, qualifierClass)) // accessible
      .filter(tagMethod -> !member.getManager().areElementsEquivalent(tagMethod, member)); // not the same
  }

  private static boolean areReplaceable(PsiField suggested, PsiReferenceExpression expression) {
    if (ExpressionUtils.isVoidContext(expression)) return true;
    PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
    if (expectedType == null) return true;
    PsiType suggestedType = suggested.getType();
    return TypeConversionUtil.isAssignable(expectedType, suggestedType);
  }

  private static boolean areReplaceable(PsiMethod suggested, PsiReferenceExpression expression) {
    if (!suggested.getParameterList().isEmpty()) return false;
    if (ExpressionUtils.isVoidContext(expression)) return true;
    PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
    if (expectedType == null) return true;
    PsiType suggestedType = suggested.getReturnType();
    return suggestedType != null && TypeConversionUtil.isAssignable(expectedType, suggestedType);
  }

  private static boolean areReplaceable(@NotNull PsiMethod initial,
                                        @NotNull PsiMethod suggestedReplacement,
                                        @NotNull PsiMethodCallExpression call) {
    boolean isInitialStatic = initial.hasModifierProperty(PsiModifier.STATIC);

    String qualifierText;
    if (isInitialStatic) {
      qualifierText = Objects.requireNonNull(suggestedReplacement.getContainingClass()).getQualifiedName() + ".";
    }
    else {
      PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
      qualifierText = qualifierExpression == null ? "" : qualifierExpression.getText() + ".";

      PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
      if (qualifier == null) return false;
      PsiClass qualifierClass = PsiUtil.resolveClassInType(qualifier.getType());
      if (qualifierClass == null) return false;
      PsiClass suggestedClass = suggestedReplacement.getContainingClass();
      if (suggestedClass == null || !InheritanceUtil.isInheritorOrSelf(qualifierClass, suggestedClass, true)) return false;
    }

    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(initial.getProject());
    PsiExpressionList arguments = call.getArgumentList();
    PsiMethodCallExpression suggestedCall = (PsiMethodCallExpression)elementFactory
      .createExpressionFromText(qualifierText + suggestedReplacement.getName() + arguments.getText(), call);

    PsiType type = ExpectedTypeUtils.findExpectedType(call, true);
    if (type != null && !type.equals(PsiTypes.voidType())) {
      PsiType suggestedCallType = suggestedCall.getType();
      if (!ExpressionUtils.isVoidContext(call) && suggestedCallType != null && !TypeConversionUtil.isAssignable(type, suggestedCallType)) {
        return false;
      }
    }

    MethodCandidateInfo result = ObjectUtils.tryCast(suggestedCall.resolveMethodGenerics(), MethodCandidateInfo.class);
    return result != null && result.isApplicable();
  }

  @Nullable
  private static PsiReferenceExpression getFieldReferenceExpression(@NotNull PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      return (PsiReferenceExpression) element;
    }
    return ObjectUtils.tryCast(element.getParent(), PsiReferenceExpression.class);
  }

  @Nullable
  private static PsiMethodCallExpression getMethodCall(@NotNull PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      return ObjectUtils.tryCast(element.getParent(), PsiMethodCallExpression.class);
    }
    if (element instanceof PsiIdentifier) {
      return getMethodCall(element.getParent());
    }
    return null;
  }
}
