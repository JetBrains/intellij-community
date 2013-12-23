package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewExpressionPostfixTemplate extends PostfixTemplate {
  public NewExpressionPostfixTemplate() {
    super("new", "Produces instantiation expression for type", "new SomeType()");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    // todo: copy paste?
    PsiElement ref = PsiTreeUtil.getParentOfType(context, PsiJavaCodeReferenceElement.class);
    if (ref instanceof PsiJavaReference) {
      PsiElement element = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      if (element instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)element;
        CtorAccessibility accessibility = calcAccessibility(psiClass, context);
        if (accessibility != CtorAccessibility.NOT_ACCESSIBLE) return true;
      }
    }
    return false;
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiElement ref = PsiTreeUtil.getParentOfType(context, PsiJavaCodeReferenceElement.class);

    if (ref instanceof PsiJavaReference) {
      PsiElement element = ((PsiJavaReference)ref).advancedResolve(true).getElement();
      if (element instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)element;
        CtorAccessibility accessibility = calcAccessibility(psiClass, context);
        if (accessibility != CtorAccessibility.NOT_ACCESSIBLE) {
          boolean typeRequiresRefinement = psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT);

          String template = "new " + ref.getText() + "()";
          if (typeRequiresRefinement) template += "{}";

          PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
          PsiNewExpression newExpression = (PsiNewExpression)factory.createExpressionFromText(template, context);
          PsiElement replace = ref.getParent().replace(newExpression);
          replace.getNode().addLeaf(JavaTokenType.SEMICOLON, ";", null);
          int offset = calculateOffset((PsiNewExpression)replace, accessibility, typeRequiresRefinement);
          editor.getCaretModel().moveToOffset(offset);
        }
      }
    }
  }

  private static int calculateOffset(PsiNewExpression expression, CtorAccessibility accessibility, boolean typeRequiresRefinement) {
    PsiExpressionList argumentList = expression.getArgumentList();
    assert argumentList != null;
    if (accessibility == CtorAccessibility.WITH_PARAMETRIC_CTOR || accessibility == CtorAccessibility.NOT_ACCESSIBLE) { // new T(<caret>)
      return argumentList.getFirstChild().getTextRange().getEndOffset();
    }
    else if (typeRequiresRefinement) {
      PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      PsiElement lBrace = anonymousClass != null ? anonymousClass.getLBrace() : null;
      if (lBrace != null) return lBrace.getTextRange().getEndOffset();
    }
    return expression.getTextRange().getEndOffset();
  }

  public enum CtorAccessibility {
    NOT_ACCESSIBLE,
    WITH_DEFAULT_CTOR,
    WITH_PARAMETRIC_CTOR
  }

  private static CtorAccessibility calcAccessibility(@Nullable PsiClass psiClass, @NotNull PsiElement accessContext) {
    if (psiClass == null) return CtorAccessibility.NOT_ACCESSIBLE;
    if (psiClass.isEnum()) return CtorAccessibility.NOT_ACCESSIBLE;
    if (psiClass.isInterface()) return CtorAccessibility.WITH_DEFAULT_CTOR;

    PsiClass containingType = PsiTreeUtil.getParentOfType(accessContext, PsiClass.class);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(accessContext.getProject());
    PsiResolveHelper resolveHelper = psiFacade.getResolveHelper();

    PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length == 0) return CtorAccessibility.WITH_DEFAULT_CTOR;

    boolean hasAccessibleCtors = false, hasParametricCtors = false;

    for (PsiMethod constructor : constructors) {
      if (resolveHelper.isAccessible(constructor, accessContext, containingType)) {
        hasAccessibleCtors = true;
        int parametersCount = constructor.getParameterList().getParametersCount();
        if (parametersCount != 0) hasParametricCtors = true;
      }
    }

    if (!hasAccessibleCtors) return CtorAccessibility.NOT_ACCESSIBLE;

    return hasParametricCtors ? CtorAccessibility.WITH_PARAMETRIC_CTOR : CtorAccessibility.WITH_DEFAULT_CTOR;
  }
}