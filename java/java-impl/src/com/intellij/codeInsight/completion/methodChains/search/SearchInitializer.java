package com.intellij.codeInsight.completion.methodChains.search;

import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import com.intellij.compilerOutputIndex.impl.UsageIndexValue;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.FactoryMap;

import java.util.*;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class SearchInitializer {
  private final static int CHAIN_SEARCH_MAGIC_RATIO = 12;

  private final List<WeightAware<MethodIncompleteSignature>> myVertices;
  private final LinkedHashMap<MethodIncompleteSignature, MethodsChain> myChains;
  private final FactoryMap<MethodIncompleteSignature, PsiMethod[]> myResolver;

  public SearchInitializer(final SortedSet<UsageIndexValue> indexValues,
                           final FactoryMap<MethodIncompleteSignature, PsiMethod[]> resolver,
                           final String targetQName,
                           final Set<String> excludedParamsTypesQNames) {
    myResolver = resolver;
    final int size = indexValues.size();
    myVertices = new ArrayList<WeightAware<MethodIncompleteSignature>>(size);
    myChains = new LinkedHashMap<MethodIncompleteSignature, MethodsChain>(size);
    add(indexValues, MethodChainsSearchUtil.unionToHashSet(excludedParamsTypesQNames, targetQName));
  }

  private void add(final Collection<UsageIndexValue> indexValues, final Set<String> excludedParamsTypesQNames) {
    int bestOccurrences = -1;
    for (final UsageIndexValue indexValue : indexValues) {
      if (add(indexValue, excludedParamsTypesQNames)) {
        final int occurrences = indexValue.getOccurrences();
        if (bestOccurrences == -1) {
          bestOccurrences = occurrences;
        }
        else if (bestOccurrences > occurrences * CHAIN_SEARCH_MAGIC_RATIO) {
          return;
        }
      }
    }
  }

  private boolean add(final UsageIndexValue indexValue, final Set<String> excludedParamsTypesQNames) {
    final MethodIncompleteSignature methodInvocation = indexValue.getMethodIncompleteSignature();
    final PsiMethod[] psiMethods = myResolver.get(methodInvocation);
    if (psiMethods.length != 0 && MethodChainsSearchUtil.checkParametersForTypesQNames(psiMethods, excludedParamsTypesQNames)) {
      final int occurrences = indexValue.getOccurrences();
      final MethodsChain methodsChain = new MethodsChain(psiMethods, occurrences, indexValue.getMethodIncompleteSignature().getOwner());
      myChains.put(methodInvocation, methodsChain);
      myVertices.add(new WeightAware<MethodIncompleteSignature>(methodInvocation, occurrences));
      return true;
    }
    return false;
  }

  public InitResult init(final Set<String> excludedEdgeNames) {
    final int size = myVertices.size();
    final List<WeightAware<MethodIncompleteSignature>> initedVertexes = new ArrayList<WeightAware<MethodIncompleteSignature>>(size);
    final LinkedHashMap<MethodIncompleteSignature, MethodsChain> initedChains =
      new LinkedHashMap<MethodIncompleteSignature, MethodsChain>(size);
    final Iterator<Map.Entry<MethodIncompleteSignature, MethodsChain>> chainsIterator = myChains.entrySet().iterator();
    for (final WeightAware<MethodIncompleteSignature> vertex : myVertices) {
      final Map.Entry<MethodIncompleteSignature, MethodsChain> chainEntry = chainsIterator.next();
      final MethodIncompleteSignature method = vertex.getUnderlying();
      if (!excludedEdgeNames.contains(method.getName())) {
        initedVertexes.add(vertex);
        final MethodsChain methodsChain = chainEntry.getValue();
        initedChains.put(chainEntry.getKey(), methodsChain);
      }
    }
    return new InitResult(initedVertexes, initedChains);
  }

  public static class InitResult {
    private final List<WeightAware<MethodIncompleteSignature>> myVertexes;
    private final LinkedHashMap<MethodIncompleteSignature, MethodsChain> myChains;

    private InitResult(final List<WeightAware<MethodIncompleteSignature>> vertexes,
                       final LinkedHashMap<MethodIncompleteSignature, MethodsChain> chains) {
      myVertexes = vertexes;
      myChains = chains;
    }

    public List<WeightAware<MethodIncompleteSignature>> getVertexes() {
      return myVertexes;
    }

    public LinkedHashMap<MethodIncompleteSignature, MethodsChain> getChains() {
      return myChains;
    }
  }
}
