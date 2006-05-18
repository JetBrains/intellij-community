package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AddTypeArgumentsFix extends FixMethodArgumentAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddTypeArgumentsFix");

  private AddTypeArgumentsFix(PsiExpressionList list, int i, PsiType toType, final ArgumentFixerActionFactory factory) {
    super(list, i, toType, factory);
  }

  @NotNull
  public String getText() {
    if (myArgList.getExpressions().length == 1) {
      return QuickFixBundle.message("add.type.arguments.single.argument.text");
    }

    return QuickFixBundle.message("add.type.arguments.text", myIndex + 1);
  }

  private static class MyFixerActionFactory extends ArgumentFixerActionFactory {
    public AddTypeArgumentsFix createFix(final PsiExpressionList list, final int i, final PsiType toType) {
      return new AddTypeArgumentsFix(list, i, toType, this);
    }

    protected PsiExpression getModifiedArgument(final PsiExpression expression, final PsiType toType) throws IncorrectOperationException {
      if (PsiUtil.getLanguageLevel(expression).compareTo(LanguageLevel.JDK_1_5) < 0) return null;

      if (expression instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
        final PsiReferenceParameterList list = methodCall.getMethodExpression().getParameterList();
        if (list == null || list.getTypeArguments().length > 0) return null;
        final JavaResolveResult resolveResult = methodCall.resolveMethodGenerics();
        final PsiElement element = resolveResult.getElement();
        if (element instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)element;
          final PsiType returnType = method.getReturnType();
          if (returnType == null) return null;

          final PsiTypeParameter[] typeParameters = method.getTypeParameters();
          if (typeParameters.length > 0) {
            PsiType[] mappings = new PsiType[typeParameters.length];
            PsiResolveHelper helper = expression.getManager().getResolveHelper();
            LanguageLevel level = PsiUtil.getLanguageLevel(expression);
            for (int i = 0; i < typeParameters.length; i++) {
              PsiTypeParameter typeParameter = typeParameters[i];
              final PsiType substitution = helper.getSubstitutionForTypeParameter(typeParameter, returnType, toType, false, level);
              if (substitution == null || PsiType.NULL.equals(substitution)) return null;
              mappings[i] = substitution;
            }

            final PsiElementFactory factory = expression.getManager().getElementFactory();
            PsiMethodCallExpression copy = (PsiMethodCallExpression)expression.copy();
            final PsiReferenceParameterList parameterList = copy.getMethodExpression().getParameterList();
            LOG.assertTrue(parameterList != null);
            for (PsiType mapping : mappings) {
              parameterList.add(factory.createTypeElement(mapping));
            }

            return copy;
          }
        }
      }

      return null;
    }

    public boolean areTypesConvertible(final PsiType exprType, final PsiType parameterType) {
      return !(exprType instanceof PsiPrimitiveType) &&
             !(parameterType instanceof PsiPrimitiveType);
    }
  }

  public static ArgumentFixerActionFactory REGISTRAR = new AddTypeArgumentsFix.MyFixerActionFactory();
}
