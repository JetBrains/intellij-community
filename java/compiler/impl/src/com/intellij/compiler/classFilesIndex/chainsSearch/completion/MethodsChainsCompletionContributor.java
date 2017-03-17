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
    ElementPattern<PsiElement> pattern = or(patternForMethodCallParameter(), patternForVariableAssignment());
    extend(COMPLETION_TYPE, pattern, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        ChainCompletionContext completionContext = extractContext(parameters);
        if (completionContext == null) return;

        Set<PsiType> contextTypesKeysSet = completionContext.getContextTypes();
        Set<PsiType> contextRelevantTypes = new HashSet<>(contextTypesKeysSet.size() + 1);
        for (PsiType type : contextTypesKeysSet) {
          if (!ChainCompletionStringUtil.isPrimitiveOrArrayOfPrimitives(type)) {
            contextRelevantTypes.add(type);
          }
        }
        TargetType target = completionContext.getTarget();
        contextRelevantTypes.remove(target.getPsiType());
        List<LookupElement> elementsFoundByMethodsChainsSearch = searchForLookups(target, contextRelevantTypes, completionContext);
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
    });
  }

  private static List<LookupElement> searchForLookups(TargetType target,
                                                      Set<PsiType> contextRelevantTypes,
                                                      ChainCompletionContext completionContext) {
    Project project = completionContext.getProject();
    CompilerReferenceServiceEx methodsUsageIndexReader = (CompilerReferenceServiceEx)CompilerReferenceService.getInstance(project);
    List<MethodsChain> searchResult =
      searchChains(target, MAX_SEARCH_RESULT_SIZE, MAX_CHAIN_SIZE, completionContext, methodsUsageIndexReader);
    if (searchResult.size() < MAX_SEARCH_RESULT_SIZE) {
      if (!target.isArray()) {
        List<MethodsChain> inheritorFilteredSearchResult = new SmartList<>();
        Processor<TargetType> consumer = targetType -> {
          for (MethodsChain chain : searchChains(targetType,
                                                 MAX_SEARCH_RESULT_SIZE,
                                                 MAX_CHAIN_SIZE,
                                                 completionContext,
                                                 methodsUsageIndexReader)) {
            boolean insert = true;
            for (MethodsChain baseChain : searchResult) {
              MethodsChain.CompareResult r = MethodsChain.compare(baseChain, chain, completionContext.getPsiManager());
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
          String inheritorQName = psiClass.getQualifiedName();
          if (inheritorQName == null) {
            return true;
          }
          return consumer.process(new TargetType(inheritorQName, false, new PsiImmediateClassType(psiClass, PsiSubstitutor.EMPTY)));
        });
      }
    }
    List<MethodsChain> chains = searchResult.size() > MAX_CHAIN_SIZE ? chooseHead(searchResult) : searchResult;
    return MethodsChainLookupRangingHelper
      .chainsToWeightableLookupElements(filterTailAndGetSumLastMethodOccurrence(chains), completionContext);
  }

  private static List<MethodsChain> chooseHead(List<MethodsChain> elements) {
    Collections.sort(elements, (o1, o2) -> o2.getChainWeight() - o1.getChainWeight());
    return elements.subList(0, MAX_CHAIN_SIZE);
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
      return ChainCompletionContext.createContext(methodParameter.getType(), null, PsiTreeUtil.getParentOfType(expression, PsiDeclarationStatement.class));
    }
    return null;
  }

  @Nullable
  private static ChainCompletionContext extractContextFromVariable(PsiLocalVariable localVariable) {
    PsiType varType = localVariable.getType();
    String varName = localVariable.getName();
    PsiDeclarationStatement declaration = PsiTreeUtil.getParentOfType(localVariable, PsiDeclarationStatement.class);
    return ChainCompletionContext.createContext(varType, varName, declaration);
  }

  @Nullable
  private static ChainCompletionContext extractContextFromAssignment(PsiAssignmentExpression assignmentExpression) {
    PsiType type = assignmentExpression.getLExpression().getType();
    PsiIdentifier identifier = PsiTreeUtil.getChildOfType(assignmentExpression.getLExpression(), PsiIdentifier.class);
    if (identifier == null) return null;
    String identifierText = identifier.getText();
    return ChainCompletionContext.createContext(type, identifierText, assignmentExpression);
  }

  private static List<MethodsChain> filterTailAndGetSumLastMethodOccurrence(List<MethodsChain> chains) {
    int maxWeight = 0;
    for (MethodsChain chain : chains) {
      int chainWeight = chain.getChainWeight();
      if (chainWeight > maxWeight) {
        maxWeight = chainWeight;
      }
    }

    List<MethodsChain> filteredResult = new ArrayList<>();
    for (MethodsChain chain : chains) {
      int chainWeight = chain.getChainWeight();
      if (chainWeight * FILTER_RATIO >= maxWeight) {
        filteredResult.add(chain);
      }
    }
    return filteredResult;
  }

  private static List<MethodsChain> searchChains(TargetType target,
                                                 int maxResultSize,
                                                 int maxChainSize,
                                                 ChainCompletionContext context,
                                                 CompilerReferenceServiceEx methodsUsageIndexReader) {
    return ChainsSearcher.search(maxChainSize, target, maxResultSize, context, methodsUsageIndexReader);
  }
}