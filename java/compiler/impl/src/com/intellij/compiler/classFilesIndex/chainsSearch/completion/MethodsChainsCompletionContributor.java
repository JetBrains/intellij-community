package com.intellij.compiler.classFilesIndex.chainsSearch.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceEx;
import com.intellij.compiler.classFilesIndex.chainsSearch.ChainCompletionStringUtil;
import com.intellij.compiler.classFilesIndex.chainsSearch.ChainsSearcher;
import com.intellij.compiler.classFilesIndex.chainsSearch.MethodsChain;
import com.intellij.compiler.classFilesIndex.chainsSearch.MethodsChainLookupRangingHelper;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.ChainCompletionContext;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.TargetType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.compiler.classFilesIndex.chainsSearch.completion.CompletionContributorPatternUtil.patternForMethodCallParameter;
import static com.intellij.compiler.classFilesIndex.chainsSearch.completion.CompletionContributorPatternUtil.patternForVariableAssignment;
import static com.intellij.patterns.PsiJavaPatterns.or;

/**
 * @author Dmitry Batkovich
 */
public class MethodsChainsCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance(MethodsChainsCompletionContributor.class);

  private static final boolean IS_UNIT_TEST_MODE = ApplicationManager.getApplication().isUnitTestMode();
  private static final int MAX_SEARCH_RESULT_SIZE = 5;
  private static final int MAX_CHAIN_SIZE = 4;
  private static final int FILTER_RATIO = 10;
  public static final CompletionType COMPLETION_TYPE = IS_UNIT_TEST_MODE ? CompletionType.BASIC : CompletionType.SMART;

  @SuppressWarnings("unchecked")
  public MethodsChainsCompletionContributor() {
    final ElementPattern<PsiElement> pattern = or(patternForMethodCallParameter(), patternForVariableAssignment());
    extend(COMPLETION_TYPE, pattern, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(final @NotNull CompletionParameters parameters,
                                    final ProcessingContext context,
                                    final @NotNull CompletionResultSet result) {
        final ChainCompletionContext completionContext = extractContext(parameters);
        if (completionContext == null) return;

        final Set<PsiType> contextTypesKeysSet = completionContext.getContextTypes();
        final Set<PsiType> contextRelevantTypes = new HashSet<>(contextTypesKeysSet.size() + 1);
        for (final PsiType type : contextTypesKeysSet) {
          if (!ChainCompletionStringUtil.isPrimitiveOrArrayOfPrimitives(type)) {
            contextRelevantTypes.add(type);
          }
        }
        final TargetType target = completionContext.getTarget();
        contextRelevantTypes.remove(target.getPsiType());
        final List<LookupElement> elementsFoundByMethodsChainsSearch = searchForLookups(target, contextRelevantTypes, completionContext);
        if (!IS_UNIT_TEST_MODE) {
          result.runRemainingContributors(parameters, completionResult -> {
            final LookupElement lookupElement = completionResult.getLookupElement();
            final PsiElement lookupElementPsi = lookupElement.getPsiElement();
            if (lookupElementPsi != null) {
              for (final LookupElement element : elementsFoundByMethodsChainsSearch) {
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
    });
  }

  private static List<LookupElement> searchForLookups(final TargetType target,
                                                      final Set<PsiType> contextRelevantTypes,
                                                      final ChainCompletionContext completionContext) {
    final Project project = completionContext.getProject();
    final CompilerReferenceServiceEx methodsUsageIndexReader = (CompilerReferenceServiceEx)CompilerReferenceService.getInstance(project);
    final List<MethodsChain> searchResult =
      searchChains(target, contextRelevantTypes, MAX_SEARCH_RESULT_SIZE, MAX_CHAIN_SIZE, completionContext, methodsUsageIndexReader);
    if (searchResult.size() < MAX_SEARCH_RESULT_SIZE) {
      if (!target.isArray()) {
        final List<MethodsChain> inheritorFilteredSearchResult = new SmartList<>();
        final Processor<TargetType> consumer = targetType -> {
          for (final MethodsChain chain : searchChains(targetType,
                                                       contextRelevantTypes,
                                                       MAX_SEARCH_RESULT_SIZE,
                                                       MAX_CHAIN_SIZE,
                                                       completionContext,
                                                       methodsUsageIndexReader)) {
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
          return searchResult.size() < MAX_SEARCH_RESULT_SIZE;
        };
        DirectClassInheritorsSearch.search(((PsiClassType)target.getPsiType()).resolve()).forEach(psiClass -> {
          final String inheritorQName = psiClass.getQualifiedName();
          if (inheritorQName == null) {
            return true;
          }
          return consumer.process(new TargetType(inheritorQName, false, new PsiImmediateClassType(psiClass, PsiSubstitutor.EMPTY)));
        });
      }
    }
    final List<MethodsChain> chains = searchResult.size() > MAX_CHAIN_SIZE ? chooseHead(searchResult) : searchResult;
    return MethodsChainLookupRangingHelper
      .chainsToWeightableLookupElements(filterTailAndGetSumLastMethodOccurrence(chains), completionContext);
  }

  private static List<MethodsChain> chooseHead(final List<MethodsChain> elements) {
    Collections.sort(elements, (o1, o2) -> o2.getChainWeight() - o1.getChainWeight());
    return elements.subList(0, MAX_CHAIN_SIZE);
  }

  @Nullable
  private static ChainCompletionContext extractContext(final CompletionParameters parameters) {
    final PsiElement parent = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiAssignmentExpression.class, PsiLocalVariable.class, PsiMethodCallExpression.class);
    LOG.assertTrue(parent != null, "A completion position should match to a pattern");

    if (parent instanceof PsiAssignmentExpression) {
      return extractContextFromAssignment((PsiAssignmentExpression)parent);
    }
    if (parent instanceof PsiLocalVariable) {
      return extractContextFromVariable((PsiLocalVariable)parent);
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
      return ChainCompletionContext.createContext(methodParameter.getType(), null, PsiTreeUtil.getParentOfType(expression, PsiDeclarationStatement.class));
    }
    return null;
  }

  @Nullable
  private static ChainCompletionContext extractContextFromVariable(PsiLocalVariable localVariable) {
    final PsiType varType = localVariable.getType();
    final String varName = localVariable.getName();
    final PsiDeclarationStatement declaration = PsiTreeUtil.getParentOfType(localVariable, PsiDeclarationStatement.class);
    return ChainCompletionContext.createContext(varType, varName, declaration);
  }

  @Nullable
  private static ChainCompletionContext extractContextFromAssignment(final PsiAssignmentExpression assignmentExpression) {
    final PsiType type = assignmentExpression.getLExpression().getType();
    final PsiIdentifier identifier = PsiTreeUtil.getChildOfType(assignmentExpression.getLExpression(), PsiIdentifier.class);
    if (identifier == null) return null;
    final String identifierText = identifier.getText();
    return ChainCompletionContext.createContext(type, identifierText, assignmentExpression);
  }

  private static List<MethodsChain> filterTailAndGetSumLastMethodOccurrence(final List<MethodsChain> chains) {
    int maxWeight = 0;
    for (final MethodsChain chain : chains) {
      final int chainWeight = chain.getChainWeight();
      if (chainWeight > maxWeight) {
        maxWeight = chainWeight;
      }
    }

    final List<MethodsChain> filteredResult = new ArrayList<>();
    for (final MethodsChain chain : chains) {
      final int chainWeight = chain.getChainWeight();
      if (chainWeight * FILTER_RATIO >= maxWeight) {
        filteredResult.add(chain);
      }
    }
    return filteredResult;
  }

  private static List<MethodsChain> searchChains(final TargetType target,
                                                 final Set<PsiType> contextVarsQNames,
                                                 final int maxResultSize,
                                                 final int maxChainSize,
                                                 final ChainCompletionContext context,
                                                 final CompilerReferenceServiceEx methodsUsageIndexReader) {
    return ChainsSearcher.search(maxChainSize, target, contextVarsQNames, maxResultSize, context, methodsUsageIndexReader);
  }
}