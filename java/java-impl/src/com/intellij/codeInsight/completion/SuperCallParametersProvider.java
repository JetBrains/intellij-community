package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Function;
import com.intellij.util.Icons;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
* @author peter
*/
class SuperCallParametersProvider extends CompletionProvider<CompletionParameters> {
  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiMethodCallExpression.class);
    assert methodCall != null;
    final PsiReferenceExpression expression = methodCall.getMethodExpression();
    if (!"super".equals(expression.getReferenceName()) && !(expression.getQualifier() instanceof PsiSuperExpression)) {
      return;
    }

    List<PsiMethod> candidates = getSuperMethodCandidates(expression);

    PsiMethod container = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);
    while (container != null) {
      for (final PsiMethod candidate : candidates) {
        if (container.getParameterList().getParametersCount() > 1 && isSuperMethod(container, candidate)) {
          result.addElement(createParametersLookupElement(container));
          return;
        }
      }

      container = PsiTreeUtil.getParentOfType(container, PsiMethod.class);

    }
  }

  private static LookupElement createParametersLookupElement(PsiMethod method) {
    final String lookupString = StringUtil.join(method.getParameterList().getParameters(), new Function<PsiParameter, String>() {
      public String fun(PsiParameter psiParameter) {
        return psiParameter.getName();
      }
    }, ", ");

    final int w = Icons.PARAMETER_ICON.getIconWidth();
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(Icons.PARAMETER_ICON, 0, 2*w/5, 0);
    icon.setIcon(Icons.PARAMETER_ICON, 1);

    final LookupElement element = LookupElementBuilder.create(lookupString).setIcon(icon);
    element.putUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS, Boolean.TRUE);

    final TailType tail = method.isConstructor() || method.getReturnType() instanceof PsiPrimitiveType
                          ? TailTypes.CALL_RPARENTH_SEMICOLON
                          : TailTypes.CALL_RPARENTH;
    return TailTypeDecorator.createDecorator(element, tail);
  }

  private static List<PsiMethod> getSuperMethodCandidates(PsiReferenceExpression expression) {
    List<PsiMethod> candidates = new ArrayList<PsiMethod>();
    for (final JavaResolveResult candidate : expression.multiResolve(true)) {
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiMethod) {
        final PsiClass psiClass = ((PsiMethod)element).getContainingClass();
        if (psiClass != null) {
          for (PsiMethod overload : psiClass.findMethodsByName(((PsiMethod)element).getName(), true)) {
            if (!overload.hasModifierProperty(PsiModifier.ABSTRACT) && !overload.hasModifierProperty(PsiModifier.STATIC)) {
              candidates.add(overload);
            }
          }
          break;
        }
      }
    }
    return candidates;
  }


  private static boolean isSuperMethod(PsiMethod container, PsiMethod superCandidate) {
    if (!container.isConstructor()) {
      return PsiSuperMethodUtil.isSuperMethod(container, superCandidate);
    }

    if (!superCandidate.isConstructor()) {
      return false;
    }
    final PsiParameter[] parameters = container.getParameterList().getParameters();
    final PsiParameter[] superParams = superCandidate.getParameterList().getParameters();
    if (superParams.length != parameters.length) {
      return false;
    }
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      final PsiParameter superParam = superParams[i];
      if (!Comparing.equal(parameter.getName(), superParam.getName()) ||
          !Comparing.equal(parameter.getType(), superParam.getType())) {
        return false;
      }
    }

    return true;
  }
}
