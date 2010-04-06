/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
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
class SameSignatureCallParametersProvider extends CompletionProvider<CompletionParameters> {
  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiMethodCallExpression.class);
    assert methodCall != null;
    final PsiReferenceExpression expression = methodCall.getMethodExpression();

    List<Pair<PsiMethod, PsiSubstitutor>> candidates = getSuperMethodCandidates(expression);

    PsiMethod container = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);
    while (container != null) {
      for (final Pair<PsiMethod, PsiSubstitutor> candidate : candidates) {
        if (container.getParameterList().getParametersCount() > 1 && isSuperMethod(container, candidate.first, candidate.second)) {
          result.addElement(createParametersLookupElement(container, methodCall));
          return;
        }
      }

      container = PsiTreeUtil.getParentOfType(container, PsiMethod.class);

    }
  }

  private static LookupElement createParametersLookupElement(PsiMethod method, PsiElement call) {
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

    return TailTypeDecorator.withTail(element, ExpectedTypesProvider.getFinalCallParameterTailType(call, method.getReturnType(), method));
  }

  private static List<Pair<PsiMethod, PsiSubstitutor>> getSuperMethodCandidates(PsiReferenceExpression expression) {
    List<Pair<PsiMethod, PsiSubstitutor>> candidates = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();
    for (final JavaResolveResult candidate : expression.multiResolve(true)) {
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiMethod) {
        final PsiClass psiClass = ((PsiMethod)element).getContainingClass();
        if (psiClass != null) {
          for (Pair<PsiMethod, PsiSubstitutor> overload : psiClass.findMethodsAndTheirSubstitutorsByName(((PsiMethod)element).getName(), true)) {
            if (!overload.first.hasModifierProperty(PsiModifier.ABSTRACT)/* && overload.first.hasModifierProperty(PsiModifier.STATIC)*/) {
              candidates.add(overload);
            }
          }
          break;
        }
      }
    }
    return candidates;
  }


  private static boolean isSuperMethod(PsiMethod container, PsiMethod callee, PsiSubstitutor substitutor) {
    if (PsiSuperMethodUtil.isSuperMethod(container, callee)) {
      return true;
    }

    final PsiParameter[] parameters = container.getParameterList().getParameters();
    final PsiParameter[] superParams = callee.getParameterList().getParameters();
    if (superParams.length != parameters.length) {
      return false;
    }
    final boolean checkNames = callee.isConstructor();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      final PsiParameter superParam = superParams[i];
      if (checkNames && !Comparing.equal(parameter.getName(), superParam.getName()) ||
          !Comparing.equal(parameter.getType(), substitutor.substitute(superParam.getType()))) {
        return false;
      }
    }

    return true;
  }
}
