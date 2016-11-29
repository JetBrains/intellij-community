package com.intellij.compiler.classFilesIndex.chainsSearch.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.compiler.classFilesIndex.api.index.ClassFilesIndexFeature;
import com.intellij.compiler.classFilesIndex.api.index.ClassFilesIndexFeaturesHolder;
import com.intellij.compiler.classFilesIndex.chainsSearch.*;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.ChainCompletionContext;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.ContextUtil;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.TargetType;
import com.intellij.compiler.classFilesIndex.impl.MethodsUsageIndexReader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.compiler.classFilesIndex.chainsSearch.completion.CompletionContributorPatternUtil.*;
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
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet result) {
    if (parameters.getInvocationCount() >= INVOCATIONS_THRESHOLD &&
        ClassFilesIndexFeaturesHolder.getInstance(parameters.getPosition().getProject())
          .enableFeatureIfNeed(ClassFilesIndexFeature.METHOD_CHAINS_COMPLETION)) {
      super.fillCompletionVariants(parameters, result);
    }
  }

  @SuppressWarnings("unchecked")
  public MethodsChainsCompletionContributor() {
    final ElementPattern<PsiElement> pattern = or(patternForMethodParameter(), patternForVariableAssignment());
    extend(COMPLETION_TYPE, pattern, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(final @NotNull CompletionParameters parameters,
                                    final ProcessingContext context,
                                    final @NotNull CompletionResultSet result) {
        final ChainCompletionContext completionContext = extractContext(parameters);
        if (completionContext == null) return;

        final Set<String> contextTypesKeysSet = completionContext.getContextTypes();
        final Set<String> contextRelevantTypes = new HashSet<>(contextTypesKeysSet.size() + 1);
        for (final String type : contextTypesKeysSet) {
          if (!ChainCompletionStringUtil.isPrimitiveOrArrayOfPrimitives(type)) {
            contextRelevantTypes.add(type);
          }
        }
        final TargetType target = completionContext.getTarget();
        contextRelevantTypes.remove(target.getClassQName());
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
                                                      final Set<String> contextRelevantTypes,
                                                      final ChainCompletionContext completionContext) {
    final Project project = completionContext.getProject();
    final MethodsUsageIndexReader methodsUsageIndexReader = MethodsUsageIndexReader.getInstance(project);
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
                                                 final Set<String> contextVarsQNames,
                                                 final int maxResultSize,
                                                 final int maxChainSize,
                                                 final ChainCompletionContext context,
                                                 final MethodsUsageIndexReader methodsUsageIndexReader) {
    return ChainsSearcher.search(maxChainSize, target, contextVarsQNames, maxResultSize, context, methodsUsageIndexReader);
  }
}