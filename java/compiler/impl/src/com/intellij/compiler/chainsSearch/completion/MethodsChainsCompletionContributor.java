/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.compiler.chainsSearch.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceEx;
import com.intellij.compiler.backwardRefs.ReferenceIndexUnavailableException;
import com.intellij.compiler.chainsSearch.ChainSearchMagicConstants;
import com.intellij.compiler.chainsSearch.ChainsSearcher;
import com.intellij.compiler.chainsSearch.MethodsChain;
import com.intellij.compiler.chainsSearch.MethodsChainLookupRangingHelper;
import com.intellij.compiler.chainsSearch.context.ChainCompletionContext;
import com.intellij.compiler.chainsSearch.context.TargetType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.patterns.PsiJavaPatterns.or;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author Dmitry Batkovich
 */
public class MethodsChainsCompletionContributor extends CompletionContributor {
  public static final String REGISTRY_KEY = "compiler.ref.chain.search";
  private static final Logger LOG = Logger.getInstance(MethodsChainsCompletionContributor.class);
  private static final boolean IS_UNIT_TEST_MODE = ApplicationManager.getApplication().isUnitTestMode();
  public static final CompletionType COMPLETION_TYPE = IS_UNIT_TEST_MODE ? CompletionType.BASIC : CompletionType.SMART;

  @SuppressWarnings("unchecked")
  public MethodsChainsCompletionContributor() {
    ElementPattern<PsiElement> pattern = or(patternForMethodCallParameter(), patternForVariableAssignment());
    extend(COMPLETION_TYPE, pattern, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        try {
          if (!Registry.is(REGISTRY_KEY)) return;
          ChainCompletionContext completionContext = extractContext(parameters);
          if (completionContext == null) return;
          List<LookupElement> elementsFoundByMethodsChainsSearch = searchForLookups(completionContext);
          if (!IS_UNIT_TEST_MODE) {
            result.runRemainingContributors(parameters, completionResult -> {
              LookupElement lookupElement = completionResult.getLookupElement();
              PsiElement lookupElementPsi = lookupElement.getPsiElement();
              if (lookupElementPsi != null) {
                for (LookupElement element : elementsFoundByMethodsChainsSearch) {
                  if (lookupElementPsi.isEquivalentTo(element.getPsiElement())) {
                    elementsFoundByMethodsChainsSearch.remove(element);
                    break;
                  }
                }
              }
              result.passResult(completionResult);
            });
          } else {
            result.stopHere();
          }
          result.addAllElements(elementsFoundByMethodsChainsSearch);
        }
        catch (ReferenceIndexUnavailableException ignored) {
          //index was closed due to compilation
        }
      }
    });
  }

  private static List<LookupElement> searchForLookups(ChainCompletionContext context) {
    CompilerReferenceServiceEx methodsUsageIndexReader = (CompilerReferenceServiceEx)CompilerReferenceService.getInstance(context.getProject());
    TargetType target = context.getTarget();
    List<MethodsChain> searchResult =
      ChainsSearcher.search(ChainSearchMagicConstants.MAX_CHAIN_SIZE,
                            target,
                            ChainSearchMagicConstants.MAX_SEARCH_RESULT_SIZE,
                            context,
                            methodsUsageIndexReader);
    int maxWeight = searchResult.stream().mapToInt(MethodsChain::getChainWeight).max().orElse(0);

    return searchResult
      .stream()
      .filter(ch -> ch.getChainWeight() * ChainSearchMagicConstants.FILTER_RATIO >= maxWeight)
      .map(ch -> MethodsChainLookupRangingHelper.chainToWeightableLookupElement(ch, context))
      .collect(Collectors.toList());
  }

  @Nullable
  private static ChainCompletionContext extractContext(CompletionParameters parameters) {
    PsiElement parent = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiAssignmentExpression.class, PsiLocalVariable.class, PsiMethodCallExpression.class);
    LOG.assertTrue(parent != null, "A completion position should match to a pattern");

    if (parent instanceof PsiAssignmentExpression) {
      return extractContextFromAssignment((PsiAssignmentExpression)parent);
    }
    if (parent instanceof PsiLocalVariable) {
      return extractContextFromVariable((PsiLocalVariable)parent);
    }
    PsiMethod method = ((PsiMethodCallExpression)parent).resolveMethod();
    if (method == null) return null;
    PsiExpression expression = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiExpression.class);
    PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiExpressionList.class);
    if (expressionList == null) return null;
    int exprPosition = Arrays.asList(expressionList.getExpressions()).indexOf(expression);
    PsiParameter[] methodParameters = method.getParameterList().getParameters();
    if (exprPosition < methodParameters.length) {
      PsiParameter methodParameter = methodParameters[exprPosition];
      return ChainCompletionContext.createContext(methodParameter.getType(), PsiTreeUtil.getParentOfType(expression, PsiDeclarationStatement.class));
    }
    return null;
  }

  @Nullable
  private static ChainCompletionContext extractContextFromVariable(PsiLocalVariable localVariable) {
    PsiDeclarationStatement declaration = PsiTreeUtil.getParentOfType(localVariable, PsiDeclarationStatement.class);
    return ChainCompletionContext.createContext(localVariable.getType(), declaration);
  }

  @Nullable
  private static ChainCompletionContext extractContextFromAssignment(PsiAssignmentExpression assignmentExpression) {
    if (!(assignmentExpression instanceof PsiReferenceExpression)) return null;
    PsiElement resolved = ((PsiReferenceExpression)assignmentExpression).resolve();
    return resolved instanceof PsiVariable
           ? ChainCompletionContext.createContext(((PsiVariable)resolved).getType(), assignmentExpression)
           : null;
  }

  @NotNull
  private static ElementPattern<PsiElement> patternForVariableAssignment() {
    final ElementPattern<PsiElement> patternForParent = or(psiElement().withText(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED)
                                                             .afterSiblingSkipping(psiElement(PsiWhiteSpace.class),
                                                                                   psiElement(PsiJavaToken.class).withText("=")));

    return psiElement().withParent(patternForParent).withSuperParent(2, or(psiElement(PsiAssignmentExpression.class),
                                                                           psiElement(PsiLocalVariable.class)
                                                                             .inside(PsiDeclarationStatement.class))).inside(PsiMethod.class);
  }

  @NotNull
  private static ElementPattern<PsiElement> patternForMethodCallParameter() {
    return psiElement().withSuperParent(3, PsiMethodCallExpressionImpl.class);
  }

  private static boolean suggestIterators(@NotNull CompletionParameters parameters) {
    return parameters.getInvocationCount() > 1;
  }
}