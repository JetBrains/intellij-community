// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiEmptyExpressionImpl;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Consumer;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;

class SameSignatureCallParametersProvider {
  static final PsiElementPattern.Capture<PsiElement> IN_CALL_ARGUMENT =
    psiElement().afterLeaf("(").withParent(
      psiElement(PsiReferenceExpression.class).withParent(
        psiElement(PsiExpressionList.class).withParent(PsiCall.class))).with(
      new PatternCondition<>("Method call completed with parameter hints") {
        @Override
        public boolean accepts(@NotNull PsiElement element, ProcessingContext context) {
          PsiElement e = element.getParent();
          while ((e = e.getNextSibling()) != null) {
            if (e instanceof PsiExpression && !(e instanceof PsiEmptyExpressionImpl)) return false;
          }
          return true;
        }
      });

  void addSignatureItems(@NotNull PsiElement position, @NotNull Consumer<? super LookupElement> result) {
    final PsiCall methodCall = PsiTreeUtil.getParentOfType(position, PsiCall.class);
    assert methodCall != null;
    Set<Pair<PsiMethod, PsiSubstitutor>> candidates = getCallCandidates(methodCall);

    PsiMethod container = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);
    while (container != null) {
      for (final Pair<PsiMethod, PsiSubstitutor> candidate : candidates) {
        if (container.getParameterList().getParametersCount() > 1 && candidate.first.getParameterList().getParametersCount() > 1) {
          PsiMethod from = getMethodToTakeParametersFrom(container, candidate.first, candidate.second);
          if (from != null) {
            result.consume(createParametersLookupElement(from, methodCall, candidate.first));
          }
        }
      }

      container = PsiTreeUtil.getParentOfType(container, PsiMethod.class);

    }
  }

  private static LookupElement createParametersLookupElement(final PsiMethod takeParametersFrom, PsiElement call, PsiMethod invoked) {
    final PsiParameter[] parameters = takeParametersFrom.getParameterList().getParameters();
    final String lookupString = StringUtil.join(parameters, PsiNamedElement::getName, ", ");

    final int w = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Parameter).getIconWidth();
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Parameter), 0, 2 * w / 5, 0);
    icon.setIcon(IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Parameter), 1);

    LookupElementBuilder element = LookupElementBuilder.create(lookupString).withIcon(icon);
    boolean makeFinalIfNeeded = PsiTreeUtil.isAncestor(takeParametersFrom, call, true);
    element = element.withInsertHandler(new InsertHandler<>() {
      @Override
      public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
        context.commitDocument();
        int startOffset = context.getTailOffset();
        PsiExpressionList exprList =
          PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), startOffset - 1, PsiExpressionList.class, false);
        PsiElement rParen = exprList == null ? null : exprList.getLastChild();
        if (rParen != null && rParen.textMatches(")")) {
          context.getDocument().deleteString(startOffset, rParen.getTextRange().getStartOffset());
        }
        if (makeFinalIfNeeded) {
          context.commitDocument();
          for (PsiParameter parameter : CompletionUtil.getOriginalOrSelf(takeParametersFrom).getParameterList().getParameters()) {
            VariableLookupItem.makeFinalIfNeeded(context, parameter);
          }
        }
      }
    });
    element.putUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS, Boolean.TRUE);

    return TailTypeDecorator.withTail(element, ExpectedTypesProvider.getFinalCallParameterTailType(call, invoked.getReturnType(), invoked));
  }

  private static Set<Pair<PsiMethod, PsiSubstitutor>> getCallCandidates(PsiCall expression) {
    PsiMethod chosenMethod = CompletionMemory.getChosenMethod(expression);
    Set<Pair<PsiMethod, PsiSubstitutor>> candidates = new LinkedHashSet<>();
    JavaResolveResult[] results;
    if (expression instanceof PsiMethodCallExpression) {
      results = ((PsiMethodCallExpression)expression).getMethodExpression().multiResolve(false);
    } else {
      results = new JavaResolveResult[]{expression.resolveMethodGenerics()};
    }

    PsiMethod toExclude =
      JavaPsiConstructorUtil.isConstructorCall(expression) ? PsiTreeUtil.getParentOfType(expression, PsiMethod.class) : null;

    for (final JavaResolveResult candidate : results) {
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiMethod) {
        final PsiClass psiClass = ((PsiMethod)element).getContainingClass();
        if (psiClass != null) {
          for (Pair<PsiMethod, PsiSubstitutor> overload : psiClass.findMethodsAndTheirSubstitutorsByName(((PsiMethod)element).getName(), true)) {
            if (overload.first != toExclude && (chosenMethod == null || chosenMethod.isEquivalentTo(overload.first))) {
              candidates.add(Pair.create(overload.first, candidate.getSubstitutor().putAll(overload.second)));
            }
          }
          break;
        }
      }
    }
    return candidates;
  }


  @Nullable
  private static PsiMethod getMethodToTakeParametersFrom(PsiMethod place, PsiMethod invoked, PsiSubstitutor substitutor) {
    if (PsiSuperMethodUtil.isSuperMethod(place, invoked)) {
      return place;
    }

    Map<String, PsiType> requiredNames = new HashMap<>();
    final PsiParameter[] parameters = place.getParameterList().getParameters();
    final PsiParameter[] callParams = invoked.getParameterList().getParameters();
    if (callParams.length > parameters.length) {
      return null;
    }

    final boolean checkNames = invoked.isConstructor();
    boolean sameTypes = true;
    for (int i = 0; i < callParams.length; i++) {
      PsiParameter callParam = callParams[i];
      PsiParameter parameter = parameters[i];
      requiredNames.put(callParam.getName(), substitutor.substitute(callParam.getType()));
      if (checkNames && !Objects.equals(parameter.getName(), callParam.getName()) ||
          !Comparing.equal(parameter.getType(), substitutor.substitute(callParam.getType()))) {
        sameTypes = false;
      }
    }

    if (sameTypes && callParams.length == parameters.length) {
      return place;
    }

    for (PsiParameter parameter : parameters) {
      PsiType type = requiredNames.remove(parameter.getName());
      if (type != null && !parameter.getType().equals(type)) {
        return null;
      }
    }

    return requiredNames.isEmpty() ? invoked : null;
  }
}
