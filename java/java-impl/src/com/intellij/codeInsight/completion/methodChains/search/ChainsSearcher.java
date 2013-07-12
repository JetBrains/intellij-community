package com.intellij.codeInsight.completion.methodChains.search;

import com.intellij.codeInsight.completion.methodChains.Constants;
import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import com.intellij.compilerOutputIndex.impl.UsageIndexValue;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class ChainsSearcher {

  public static List<MethodsChain> search(final MethodChainsSearchService searchService,
                                          final String targetQName,
                                          final Set<String> contextQNames,
                                          final int maxResultSize,
                                          final int pathMaximalLength,
                                          final FactoryMap<MethodIncompleteSignature, PsiMethod[]> resolver,
                                          final String contextMethodName) {
    return search(searchService, targetQName, contextQNames, maxResultSize, pathMaximalLength, resolver,
                  Collections.<String>singleton(targetQName), contextMethodName);
  }

  public static List<MethodsChain> search(final MethodChainsSearchService searchService,
                                          final String targetQName,
                                          final Set<String> contextQNames,
                                          final int maxResultSize,
                                          final int pathMaximalLength,
                                          final FactoryMap<MethodIncompleteSignature, PsiMethod[]> resolver,
                                          final Set<String> excludedParamsTypesQNames,
                                          final String contextMethodName) {
    final SearchInitializer initializer = createInitializer(targetQName, resolver, searchService, excludedParamsTypesQNames);
    final ArrayList<MethodsChain> methodsChains = new ArrayList<MethodsChain>(maxResultSize);
    final MethodsChain firstBestMethodsChain =
      search(searchService, initializer, contextQNames, Collections.<String>emptySet(), pathMaximalLength, resolver, targetQName,
             excludedParamsTypesQNames, contextMethodName);
    if (firstBestMethodsChain != null) {
      methodsChains.add(firstBestMethodsChain);
      Set<Set<String>> excludedCombinations = MethodsChain.edgeCombinations(Collections.<Set<String>>emptySet(), firstBestMethodsChain);
      while (methodsChains.size() <= maxResultSize) {
        final Set<Set<String>> localExcludedCombinations = excludedCombinations;
        boolean allLocalsIsNull = true;
        final int beforeStepChainsCount = methodsChains.size();
        for (final Set<String> excludedEdges : localExcludedCombinations) {
          final MethodsChain local =
            search(searchService, initializer, contextQNames, excludedEdges, pathMaximalLength, resolver, targetQName,
                   excludedParamsTypesQNames, contextMethodName);
          if (local != null) {
            allLocalsIsNull = false;
          }
          else {
            continue;
          }
          boolean add = true;
          for (int i = 0; i < methodsChains.size(); i++) {
            final MethodsChain chain = methodsChains.get(i);
            final MethodsChain.CompareResult compareResult = MethodsChain.compare(local, chain);
            if (compareResult == MethodsChain.CompareResult.EQUAL || compareResult == MethodsChain.CompareResult.RIGHT_CONTAINS_LEFT) {
              add = false;
              break;
            }
            else if (compareResult == MethodsChain.CompareResult.LEFT_CONTAINS_RIGHT) {
              methodsChains.set(i, local);
              add = false;
              break;
            }
          }
          if (add) {
            methodsChains.add(local);
            if (methodsChains.size() >= maxResultSize) {
              return methodsChains;
            }
            excludedCombinations = MethodsChain.edgeCombinations(excludedCombinations, local);
          }
        }
        if (allLocalsIsNull || beforeStepChainsCount == methodsChains.size()) {
          return methodsChains;
        }
      }
    }
    return methodsChains;
  }

  private static SearchInitializer createInitializer(final String targetQName,
                                                     final FactoryMap<MethodIncompleteSignature, PsiMethod[]> context,
                                                     final MethodChainsSearchService searchService,
                                                     final Set<String> excludedParamsTypesQNames) {
    return new SearchInitializer(searchService.getMethods(targetQName), context, targetQName, excludedParamsTypesQNames);
  }

  @Nullable
  private static MethodsChain search(final MethodChainsSearchService searchService,
                                     final SearchInitializer initializer,
                                     final Set<String> toSet,
                                     final Set<String> excludedEdgeNames,
                                     final int pathMaximalLength,
                                     final FactoryMap<MethodIncompleteSignature, PsiMethod[]> resolver,
                                     final String targetQName,
                                     final Set<String> excludedParamsTypesQNames,
                                     final String contextMethodName) {
    final Set<String> allExcludedNames = MethodChainsSearchUtil.unionToHashSet(excludedParamsTypesQNames, targetQName);
    ProgressManager.checkCanceled();
    final SearchInitializer.InitResult initResult = initializer.init(excludedEdgeNames, toSet, searchService, contextMethodName);
    final Map<MethodIncompleteSignature, MethodsChain> knownDistance = initResult.getChains();
    final PriorityQueue<WeightAware<MethodIncompleteSignature>> q =
      new PriorityQueue<WeightAware<MethodIncompleteSignature>>(initResult.getVertexes());
    MethodsChain result = initResult.getCurrentBestTargetChain();

    int maxWeight = 0;
    for (final MethodsChain methodsChain : knownDistance.values()) {
      if (methodsChain.getChainWeight() > maxWeight) {
        maxWeight = methodsChain.getChainWeight();
      }
    }

    final WeightAware<MethodIncompleteSignature> maxVertex = q.peek();
    final int maxDistance;
    if (maxVertex != null) {
      maxDistance = maxVertex.getWeight();
    }
    else {
      return null;
    }

    while (!q.isEmpty()) {
      final WeightAware<MethodIncompleteSignature> currentVertex = q.poll();
      final int currentVertexDistance = currentVertex.getWeight();
      if (currentVertexDistance * Constants.CHAIN_SEARCH_MAGIC_RATIO < maxDistance) {
        return result;
      }
      final MethodIncompleteSignature currentVertexUnderlying = currentVertex.getUnderlying();
      final MethodsChain currentVertexMethodsChain = knownDistance.get(currentVertexUnderlying);
      if (currentVertexDistance != currentVertexMethodsChain.getChainWeight()) {
        continue;
      }
      final SortedSet<UsageIndexValue> bigrams = searchService.getBigram(currentVertexUnderlying);
      int bigramsSumWeight = 0;
      int maxUpdatedWeight = 0;
      for (final UsageIndexValue indexValue : bigrams) {
        final MethodIncompleteSignature vertex = indexValue.getMethodIncompleteSignature();
        final int occurrences = indexValue.getOccurrences();
        bigramsSumWeight += occurrences;
        final boolean canBeResult = vertex.isStatic() || toSet.contains(vertex.getOwner());
        if (!vertex.getOwner().equals(targetQName) || canBeResult) {
          final int vertexDistance = Math.min(currentVertexDistance, occurrences);
          final MethodsChain knownVertexMethodsChain = knownDistance.get(vertex);
          if ((knownVertexMethodsChain == null || knownVertexMethodsChain.getChainWeight() < vertexDistance) &&
              (result == null || result.getChainWeight() < vertexDistance)) {
            if (occurrences * Constants.CHAIN_SEARCH_MAGIC_RATIO >= currentVertexMethodsChain.getChainWeight()) {
              final MethodIncompleteSignature methodInvocation = indexValue.getMethodIncompleteSignature();
              final PsiMethod[] psiMethods = resolver.get(methodInvocation);

              if (psiMethods.length != 0 && MethodChainsSearchUtil.checkParametersForTypesQNames(psiMethods, allExcludedNames)) {
                final MethodsChain newBestMethodsChain = currentVertexMethodsChain.addEdge(psiMethods);
                if (canBeResult) {
                  result = newBestMethodsChain;
                }
                else if (newBestMethodsChain.size() < pathMaximalLength - 1) {
                  maxUpdatedWeight = Math.max(maxUpdatedWeight, newBestMethodsChain.getChainWeight());
                  q.add(new WeightAware<MethodIncompleteSignature>(indexValue.getMethodIncompleteSignature(),
                                                                   newBestMethodsChain.getChainWeight()));
                }
                knownDistance.put(vertex, newBestMethodsChain);
              }
            }
            else if (!allExcludedNames.contains(currentVertexMethodsChain.getFirstQualifierClass().getQualifiedName()) &&
                     searchService.isSingleton(currentVertexMethodsChain.getFirstQualifierClass(), contextMethodName) &&
                     (searchService.isRelevantMethodForNotOverriden(currentVertexMethodsChain.getFirstQualifierClass().getQualifiedName(),
                                                                    currentVertexMethodsChain.getOneOfFirst().getName()) ||
                      searchService.isRelevantMethodForField(currentVertexMethodsChain.getFirstQualifierClass().getQualifiedName(),
                                                             currentVertexMethodsChain.getOneOfFirst().getName()))) {
              result = currentVertexMethodsChain;
            }
          }
        }
      }
      //if ((result == null ||  maxUpdatedWeight * Constants.CHAIN_SEARCH_MAGIC_RATIO2 <= bigramsSumWeight)
      //    && bigramsSumWeight * Constants.CHAIN_SEARCH_MAGIC_RATIO >= currentVertexMethodsChain.getChainWeight()) {
      //  return currentVertexMethodsChain;
      //}

      if ((currentVertexMethodsChain.isStaticChain() ||
           !allExcludedNames.contains(currentVertexMethodsChain.getFirstQualifierClass().getQualifiedName())) &&
          bigramsSumWeight * Constants.CHAIN_SEARCH_MAGIC_RATIO <= currentVertexDistance &&
          (result == null || result.getChainWeight() < currentVertexDistance) &&
          (currentVertexMethodsChain.isStaticChain() ||
           searchService.isSingleton(currentVertexMethodsChain.getFirstQualifierClass(), contextMethodName) &&
          (searchService.isRelevantMethodForNotOverriden(currentVertexMethodsChain.getFirstQualifierClass().getQualifiedName(),
                                                         currentVertexMethodsChain.getOneOfFirst().getName()) ||
           searchService.isRelevantMethodForField(currentVertexMethodsChain.getFirstQualifierClass().getQualifiedName(),
                                                  currentVertexMethodsChain.getOneOfFirst().getName())))) {
        result = currentVertexMethodsChain;
      }
    }

    if (result != null && result.getChainWeight() * Constants.CHAIN_SEARCH_MAGIC_RATIO >= maxWeight) {
      return result;
    }
    return null;
  }
}
