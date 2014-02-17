/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.chainsSearch.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.compiler.classFilesIndex.api.index.ClassFilesIndexFeature;
import com.intellij.compiler.classFilesIndex.api.index.ClassFilesIndexFeaturesHolder;
import com.intellij.compiler.classFilesIndex.chainsSearch.*;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.ChainCompletionContext;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.ContextUtil;
import com.intellij.compiler.classFilesIndex.impl.MethodsUsageIndexReader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PsiJavaPatterns.or;

/**
 * @author Dmitry Batkovich
 */
public class MethodsChainsCompletionContributor extends CompletionContributor {
  private final static boolean IS_UNIT_TEST_MODE = ApplicationManager.getApplication().isUnitTestMode();

  public static final int INVOCATIONS_THRESHOLD = 2;
  public static final CompletionType COMPLETION_TYPE = IS_UNIT_TEST_MODE ? CompletionType.BASIC : CompletionType.SMART;

  private final static int MAX_SEARCH_RESULT_SIZE = 5;
  private final static int MAX_CHAIN_SIZE = 4;
  private final static int FILTER_RATIO = 10;

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getInvocationCount() >= INVOCATIONS_THRESHOLD
        && ClassFilesIndexFeaturesHolder.getInstance(parameters.getPosition().getProject())
          .enableFeatureIfNeed(ClassFilesIndexFeature.METHOD_CHAINS_COMPLETION)) {
      super.fillCompletionVariants(parameters, result);
    }
  }

  @SuppressWarnings("unchecked")
  public MethodsChainsCompletionContributor() {
    final ElementPattern<PsiElement> pattern =
      or(CompletionContributorPatternUtil.patternForMethodParameter(), CompletionContributorPatternUtil.patternForVariableAssignment());
    extend(COMPLETION_TYPE, pattern, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(final @NotNull CompletionParameters parameters,
                                    final ProcessingContext context,
                                    final @NotNull CompletionResultSet result) {
        final ChainCompletionContext completionContext = extractContext(parameters);
        if (completionContext == null) return;

        final String targetClassQName = completionContext.getTargetQName();
        final Set<String> contextTypesKeysSet = completionContext.getContextTypes();
        final Set<String> contextRelevantTypes = new HashSet<String>(contextTypesKeysSet.size() + 1);
        for (final String type : contextTypesKeysSet) {
          if (!ChainCompletionStringUtil.isPrimitiveOrArrayOfPrimitives(type)) {
            contextRelevantTypes.add(type);
          }
        }
        contextRelevantTypes.remove(targetClassQName);

        final List<LookupElement> foundElements = searchForLookups(targetClassQName, contextRelevantTypes, completionContext);
        if (!IS_UNIT_TEST_MODE) {
          result.runRemainingContributors(parameters, new Consumer<CompletionResult>() {
            @Override
            public void consume(final CompletionResult completionResult) {
              final LookupElement lookupElement = completionResult.getLookupElement();
              final PsiElement lookupElementPsi = lookupElement.getPsiElement();
              if (lookupElementPsi != null) {
                for (final LookupElement element : foundElements) {
                  if (lookupElementPsi.isEquivalentTo(element.getPsiElement())) {
                    foundElements.remove(element);
                    break;
                  }
                }
              }
              result.passResult(completionResult);
            }
          });
        } else {
          result.stopHere();
        }
        result.addAllElements(foundElements);
      }
    });
  }

  private static List<LookupElement> searchForLookups(final String targetClassQName,
                                                      final Set<String> contextRelevantTypes,
                                                      final ChainCompletionContext completionContext) {
    final MethodsUsageIndexReader methodsUsageIndexReader = MethodsUsageIndexReader.getInstance(completionContext.getProject());
    final List<MethodsChain> searchResult =
      searchChains(targetClassQName, contextRelevantTypes, MAX_SEARCH_RESULT_SIZE, MAX_CHAIN_SIZE, completionContext, methodsUsageIndexReader);
    if (searchResult.size() < MAX_SEARCH_RESULT_SIZE) {
      final PsiClass aClass = JavaPsiFacade.getInstance(completionContext.getProject())
        .findClass(targetClassQName, GlobalSearchScope.allScope(completionContext.getProject()));
      if (aClass != null) {
        DirectClassInheritorsSearch.search(aClass).forEach(new Processor<PsiClass>() {
          @Override
          public boolean process(final PsiClass psiClass) {
            final String inheritorQName = psiClass.getQualifiedName();
            if (!StringUtil.isEmpty(inheritorQName)) {
              final List<MethodsChain> inheritorFilteredSearchResult = new SmartList<MethodsChain>();
              //noinspection ConstantConditions
              for (final MethodsChain chain : searchChains(inheritorQName, contextRelevantTypes, MAX_SEARCH_RESULT_SIZE, MAX_CHAIN_SIZE,
                                                           completionContext, methodsUsageIndexReader)) {
                boolean insert = true;
                for (final MethodsChain baseChain : searchResult) {
                  final MethodsChain.CompareResult r = MethodsChain.compare(baseChain, chain, completionContext.getPsiManager());
                  if (r != MethodsChain.CompareResult.NOT_EQUAL) {
                    insert = false;
                    break;
                  }
                }
                if (insert) {
                  inheritorFilteredSearchResult.add(chain);
                }
              }
              searchResult.addAll(inheritorFilteredSearchResult);
            }
            return true;
          }
        });
      }
    }
    final List<MethodsChain> chains = searchResult.size() > MAX_CHAIN_SIZE ? chooseHead(searchResult) : searchResult;
    return MethodsChainLookupRangingHelper
      .chainsToWeightableLookupElements(filterTailAndGetSumLastMethodOccurrence(chains), completionContext);
  }

  private static List<MethodsChain> chooseHead(final List<MethodsChain> elements) {
    Collections.sort(elements, new Comparator<MethodsChain>() {
      @Override
      public int compare(final MethodsChain o1, final MethodsChain o2) {
        return o2.getChainWeight() - o1.getChainWeight();
      }
    });
    return elements.subList(0, MAX_CHAIN_SIZE);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private static ChainCompletionContext extractContext(final CompletionParameters parameters) {
    final PsiElement parent = PsiTreeUtil
      .getParentOfType(parameters.getPosition(), PsiAssignmentExpression.class, PsiLocalVariable.class, PsiMethodCallExpression.class);
    if (parent == null) {
      return null;
    }

    if (parent instanceof PsiAssignmentExpression) {
      return tryExtractContextFromAssignment((PsiAssignmentExpression)parent);
    }
    if (parent instanceof PsiLocalVariable) {
      final PsiLocalVariable localVariable = (PsiLocalVariable)parent;
      return ContextUtil.createContext(localVariable.getType(), localVariable.getName(),
                                       PsiTreeUtil.getParentOfType(parent, PsiDeclarationStatement.class));
    }
    final PsiMethod method = ((PsiMethodCallExpression)parent).resolveMethod();
    if (method == null) return null;
    final PsiExpression expression = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiExpression.class);
    final PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiExpressionList.class);
    if (expressionList == null) return null;
    final int exprPosition = Arrays.asList(expressionList.getExpressions()).indexOf(expression);
    final PsiParameter[] methodParameters = method.getParameterList().getParameters();
    if (exprPosition < methodParameters.length) {
      final PsiParameter methodParameter = methodParameters[exprPosition];
      return ContextUtil
        .createContext(methodParameter.getType(), null, PsiTreeUtil.getParentOfType(expression, PsiDeclarationStatement.class));
    }
    return null;
  }

  @Nullable
  private static ChainCompletionContext tryExtractContextFromAssignment(final PsiAssignmentExpression assignmentExpression) {
    final PsiType type = assignmentExpression.getLExpression().getType();
    final PsiIdentifier identifier = PsiTreeUtil.getChildOfType(assignmentExpression.getLExpression(), PsiIdentifier.class);
    if (identifier == null) return null;
    final String identifierText = identifier.getText();
    return ContextUtil.createContext(type, identifierText, assignmentExpression);
  }

  private static List<MethodsChain> filterTailAndGetSumLastMethodOccurrence(final List<MethodsChain> chains) {
    int maxWeight = 0;
    for (final MethodsChain chain : chains) {
      final int chainWeight = chain.getChainWeight();
      if (chainWeight > maxWeight) {
        maxWeight = chainWeight;
      }
    }

    final List<MethodsChain> filteredResult = new ArrayList<MethodsChain>();
    for (final MethodsChain chain : chains) {
      final int chainWeight = chain.getChainWeight();
      if (chainWeight * FILTER_RATIO >= maxWeight) {
        filteredResult.add(chain);
      }
    }
    return filteredResult;
  }

  private static List<MethodsChain> searchChains(final String targetQName,
                                                 final Set<String> contextVarsQNames,
                                                 final int maxResultSize,
                                                 final int maxChainSize,
                                                 final ChainCompletionContext context,
                                                 final MethodsUsageIndexReader methodsUsageIndexReader) {
    return ChainsSearcher.search(methodsUsageIndexReader, targetQName, contextVarsQNames, maxResultSize, maxChainSize, context);
  }
}
