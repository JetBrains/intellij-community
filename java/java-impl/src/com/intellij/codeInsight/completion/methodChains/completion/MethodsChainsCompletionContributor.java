package com.intellij.codeInsight.completion.methodChains.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.methodChains.ChainCompletionStringUtil;
import com.intellij.codeInsight.completion.methodChains.completion.context.ChainCompletionContext;
import com.intellij.codeInsight.completion.methodChains.completion.context.ContextUtil;
import com.intellij.codeInsight.completion.methodChains.search.ChainsSearcher;
import com.intellij.codeInsight.completion.methodChains.search.MethodChainsSearchService;
import com.intellij.codeInsight.completion.methodChains.search.MethodsChain;
import com.intellij.codeInsight.completion.methodChains.search.MethodsChainLookupRangingHelper;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputIndexFeature;
import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PsiJavaPatterns.or;

/**
 * @author Dmitry Batkovich
 */
public class MethodsChainsCompletionContributor extends CompletionContributor {
  public static final int INVOCATIONS_THRESHOLD = 3;

  private final static int MAX_SEARCH_RESULT_SIZE = 5;
  private final static int MAX_CHAIN_SIZE = 4;
  private final static int FILTER_RATIO = 10;

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getInvocationCount() >= INVOCATIONS_THRESHOLD && CompilerOutputIndexFeature.METHOD_CHAINS_COMPLETION.isEnabled()) {
      super.fillCompletionVariants(parameters, result);
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        result.stopHere();
      }
    }
  }

  @SuppressWarnings("unchecked")
  public MethodsChainsCompletionContributor() {
    final ElementPattern<PsiElement> pattern =
      or(CompletionContributorPatternUtil.patternForMethodParameter(), CompletionContributorPatternUtil.patternForVariableAssignment());
    extend(CompletionType.BASIC, pattern, new CompletionProvider<CompletionParameters>() {
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
        result.addAllElements(foundElements);
      }
    });
  }

  private static List<LookupElement> searchForLookups(final String targetClassQName,
                                                      final Set<String> contextRelevantTypes,
                                                      final ChainCompletionContext completionContext) {
    final MethodChainsSearchService searchService = new MethodChainsSearchService(completionContext.getProject());
    final List<MethodsChain> searchResult =
      searchChains(targetClassQName, contextRelevantTypes, MAX_SEARCH_RESULT_SIZE, MAX_CHAIN_SIZE, completionContext, searchService);
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
                                                           completionContext, searchService)) {
                boolean insert = true;
                for (final MethodsChain baseChain : searchResult) {
                  final MethodsChain.CompareResult r = MethodsChain.compare(baseChain, chain, completionContext);
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
                                                 final MethodChainsSearchService searchService) {
    return ChainsSearcher.search(searchService, targetQName, contextVarsQNames, maxResultSize, maxChainSize,
                                 createNotDeprecatedMethodsResolver(JavaPsiFacade.getInstance(context.getProject()),
                                                                    context.getResolveScope()), context.getExcludedQNames(), context);
  }

  private static FactoryMap<MethodIncompleteSignature, PsiMethod[]> createNotDeprecatedMethodsResolver(final JavaPsiFacade javaPsiFacade,
                                                                                                       final GlobalSearchScope scope) {
    return new FactoryMap<MethodIncompleteSignature, PsiMethod[]>() {
      @Nullable
      @Override
      protected PsiMethod[] create(final MethodIncompleteSignature signature) {
        return signature.resolveNotDeprecated(javaPsiFacade, scope);
      }
    };
  }

}
