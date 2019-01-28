// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.deprecation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.application.ApplicationManager;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

public abstract class DeprecationInspectionBase extends LocalInspectionTool {
  public boolean IGNORE_IN_SAME_OUTERMOST_CLASS;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  public static void checkDeprecated(@NotNull PsiElement element,
                                     @NotNull PsiElement elementToHighlight,
                                     @Nullable TextRange rangeInElement,
                                     boolean ignoreInsideDeprecated,
                                     boolean ignoreImportStatements,
                                     boolean ignoreMethodsOfDeprecated,
                                     boolean ignoreInSameOutermostClass,
                                     @NotNull ProblemsHolder holder,
                                     boolean forRemoval,
                                     @NotNull ProblemHighlightType highlightType) {
    if (!(element instanceof PsiModifierListOwner)) {
      return;
    }

    PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)element;
    if (PsiImplUtil.isDeprecated(modifierListOwner)) {
      if (forRemoval != isForRemovalAttributeSet(modifierListOwner)) {
        return;
      }
    }
    else {
      if (!ignoreMethodsOfDeprecated) {
        PsiClass containingClass = element instanceof PsiMember ? ((PsiMember)element).getContainingClass() : null;
        if (containingClass != null) {
          checkDeprecated(containingClass, elementToHighlight, rangeInElement, ignoreInsideDeprecated, ignoreImportStatements,
                          false, ignoreInSameOutermostClass, holder, forRemoval, highlightType);
        }
      }
      return;
    }

    if (ignoreInSameOutermostClass && areElementsInSameOutermostClass(element, elementToHighlight)) return;

    if (ignoreInsideDeprecated && isElementInsideDeprecated(elementToHighlight)) return;

    if (ignoreImportStatements && isElementInsideImportStatement(elementToHighlight)) return;

    String description = JavaErrorMessages.message(forRemoval ? "marked.for.removal.symbol" : "deprecated.symbol",
                                                   getPresentableName(element));

    LocalQuickFix replacementQuickFix = getReplacementQuickFix(element, elementToHighlight);

    holder.registerProblem(elementToHighlight, getDescription(description, forRemoval, highlightType), highlightType, rangeInElement,
                           replacementQuickFix);
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
  private static LocalQuickFix getReplacementQuickFix(@NotNull PsiElement refElement, @NotNull PsiElement elementToHighlight) {
    PsiMethodCallExpression methodCall = getMethodCall(elementToHighlight);
    if (refElement instanceof PsiMethod && methodCall != null) {
      PsiMethod replacement = findReplacementInJavaDoc((PsiMethod)refElement, methodCall);
      if (replacement != null) {
        return new ReplaceMethodCallFix((PsiMethodCallExpression)elementToHighlight.getParent().getParent(), replacement);
      }
    }
    if (refElement instanceof PsiField) {
      PsiReferenceExpression referenceExpression = ObjectUtils.tryCast(elementToHighlight.getParent(), PsiReferenceExpression.class);
      if (referenceExpression != null) {
        PsiField replacement = findReplacementInJavaDoc((PsiField)refElement, referenceExpression);
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
      () -> new CachedValueProvider.Result<>(PsiUtil.getTopLevelClass(refElement), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT)
    );
    return outermostClass != null && outermostClass == PsiUtil.getTopLevelClass(elementToHighlight);
  }

  static void addSameOutermostClassCheckBox(MultipleCheckboxOptionsPanel panel) {
    panel.addCheckbox("Ignore in the same outermost class", "IGNORE_IN_SAME_OUTERMOST_CLASS");
  }

  protected static String getDescription(@NotNull String description, boolean forRemoval, ProblemHighlightType highlightType) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ProblemHighlightType defaultType = forRemoval ? ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL : ProblemHighlightType.LIKE_DEPRECATED;
      if (highlightType != defaultType) {
        return description + "(" + highlightType + ")";
      }
    }
    return description;
  }

  private static PsiField findReplacementInJavaDoc(@NotNull PsiField field, @NotNull PsiReferenceExpression referenceExpression) {
    return getReplacementCandidatesFromJavadoc(field, PsiField.class, field, getQualifierClass(referenceExpression))
      .filter(tagField -> areReplaceable(tagField, referenceExpression))
      .collect(MoreCollectors.onlyOne())
      .orElse(null);
  }

  private static PsiMethod findReplacementInJavaDoc(@NotNull PsiMethod method, @NotNull PsiMethodCallExpression call) {
    if (method instanceof PsiConstructorCall) return null;
    if (method instanceof ClsMethodImpl) {
      PsiMethod sourceMethod = ((ClsMethodImpl)method).getSourceMirrorMethod();
      return sourceMethod == null ? null : findReplacementInJavaDoc(sourceMethod, call);
    }

    return getReplacementCandidatesFromJavadoc(method, PsiMethod.class, call, getQualifierClass(call.getMethodExpression()))
      .filter(tagMethod -> areReplaceable(method, tagMethod, call))
      .collect(MoreCollectors.onlyOne())
      .orElse(null);
  }

  @NotNull
  private static <T extends PsiDocCommentOwner> Stream<? extends T> getReplacementCandidatesFromJavadoc(T member, Class<T> clazz, PsiElement context, PsiClass qualifierClass) {
    PsiDocComment doc = member.getDocComment();
    if (doc == null) return Stream.empty();

    Collection<PsiDocTag> docTags = PsiTreeUtil.findChildrenOfType(doc, PsiDocTag.class);
    if (docTags.isEmpty()) return Stream.empty();
    return docTags
      .stream()
      .filter(t -> {
        String name = t.getName();
        return "link".equals(name) || "see".equals(name);
      })
      .map(tag -> tag.getValueElement())
      .filter(Objects::nonNull)
      .map(value -> value.getReference())
      .filter(Objects::nonNull)
      .map(reference -> reference.resolve())
      .distinct()
      .map(resolved -> ObjectUtils.tryCast(resolved, clazz))
      .filter(Objects::nonNull)
      .filter(tagMethod -> !tagMethod.isDeprecated()) // not deprecated
      .filter(tagMethod -> PsiResolveHelper.SERVICE.getInstance(context.getProject()).isAccessible(tagMethod, context, qualifierClass)) // accessible
      .filter(tagMethod -> !member.getManager().areElementsEquivalent(tagMethod, member)); // not the same
  }

  private static boolean areReplaceable(PsiField suggested, PsiReferenceExpression expression) {
    if (ExpressionUtils.isVoidContext(expression)) return true;
    PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
    if (expectedType == null) return true;
    PsiType suggestedType = suggested.getType();
    return TypeConversionUtil.isAssignable(expectedType, suggestedType);
  }

  private static boolean areReplaceable(@NotNull PsiMethod initial,
                                        @NotNull PsiMethod suggestedReplacement,
                                        @NotNull PsiMethodCallExpression call) {
    boolean isInitialStatic = initial.hasModifierProperty(PsiModifier.STATIC);

    String qualifierText;
    if (isInitialStatic) {
      qualifierText = ObjectUtils.notNull(suggestedReplacement.getContainingClass()).getQualifiedName() + ".";
    }
    else {
      PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
      qualifierText = qualifierExpression == null ? "" : qualifierExpression.getText() + ".";
    }

    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(initial.getProject());
    PsiExpressionList arguments = call.getArgumentList();
    PsiMethodCallExpression suggestedCall = (PsiMethodCallExpression)elementFactory
      .createExpressionFromText(qualifierText + suggestedReplacement.getName() + arguments.getText(), call);

    PsiType type = ExpectedTypeUtils.findExpectedType(call, true);
    if (type != null && !type.equals(PsiType.VOID)) {
      PsiType suggestedCallType = suggestedCall.getType();
      if (!ExpressionUtils.isVoidContext(call) && suggestedCallType != null && !TypeConversionUtil.isAssignable(type, suggestedCallType)) {
        return false;
      }
    }

    MethodCandidateInfo result = ObjectUtils.tryCast(suggestedCall.resolveMethodGenerics(), MethodCandidateInfo.class);
    return result != null && result.isApplicable();
  }

  @Nullable
  private static PsiClass getQualifierClass(@NotNull PsiReferenceExpression referenceExpression) {
    PsiExpression expression = referenceExpression.getQualifierExpression();
    if (expression == null) {
      return RefactoringChangeUtil.getThisClass(referenceExpression);
    }
    return PsiUtil.resolveClassInType(expression.getType());
  }

  @Nullable
  private static PsiMethodCallExpression getMethodCall(@NotNull PsiElement element) {
    if (!(element instanceof PsiIdentifier)) return null;
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiReferenceExpression)) return null;
    return ObjectUtils.tryCast(parent.getParent(), PsiMethodCallExpression.class);
  }
}
