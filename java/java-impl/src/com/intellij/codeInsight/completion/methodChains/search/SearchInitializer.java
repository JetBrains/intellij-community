package com.intellij.codeInsight.completion.methodChains.search;

import com.intellij.codeInsight.completion.methodChains.Constants;
import com.intellij.compilerOutputIndex.impl.MethodIncompleteSignature;
import com.intellij.compilerOutputIndex.impl.UsageIndexValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class SearchInitializer {
  private final List<WeightAware<MethodIncompleteSignature>> myVertexes;
  private final LinkedHashMap<MethodIncompleteSignature, MethodsChain> myChains;
  private final Map<MethodIncompleteSignature, Integer> myOccurrencesMap;
  private final FactoryMap<MethodIncompleteSignature, PsiMethod[]> myResolver;

  public SearchInitializer(final SortedSet<UsageIndexValue> indexValues,
                           final FactoryMap<MethodIncompleteSignature, PsiMethod[]> resolver,
                           final String targetQName,
                           final Set<String> excludedParamsTypesQNames) {
    myResolver = resolver;
    final int size = indexValues.size();
    myVertexes = new ArrayList<WeightAware<MethodIncompleteSignature>>(size);
    myChains = new LinkedHashMap<MethodIncompleteSignature, MethodsChain>(size);
    myOccurrencesMap = new HashMap<MethodIncompleteSignature, Integer>(size);
    add(indexValues, MethodChainsSearchUtil.unionToHashSet(excludedParamsTypesQNames, targetQName));
  }

  private void add(final Collection<UsageIndexValue> indexValues, final Set<String> excludedParamsTypesQNames) {
    int bestOccurrences = -1;
    for (final UsageIndexValue indexValue : indexValues) {
      if (add(indexValue, excludedParamsTypesQNames)) {
        final int occurrences = indexValue.getOccurrences();
        if (bestOccurrences == -1) {
          bestOccurrences = occurrences;
        } else if (bestOccurrences > occurrences * Constants.CHAIN_SEARCH_MAGIC_RATIO) {
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
      final MethodsChain methodsChain = new MethodsChain(psiMethods, occurrences);
      myChains.put(methodInvocation, methodsChain);
      myVertexes.add(new WeightAware<MethodIncompleteSignature>(methodInvocation, occurrences));
      myOccurrencesMap.put(methodInvocation, occurrences);
      return true;
    }
    return false;
  }

  public InitResult init(final Set<String> excludedEdgeNames,
                         final Set<String> contextQNames,
                         final MethodChainsSearchService searchService,
                         final String contextMethodName) {
    final int size = myVertexes.size();
    int bestOccurrences = 0;
    MethodsChain bestTargetMethodChain = null;
    final List<WeightAware<MethodIncompleteSignature>> initedVertexes = new ArrayList<WeightAware<MethodIncompleteSignature>>(size);
    final LinkedHashMap<MethodIncompleteSignature, MethodsChain> initedChains = new LinkedHashMap<MethodIncompleteSignature, MethodsChain>(size);
    final Iterator<Map.Entry<MethodIncompleteSignature, MethodsChain>> chainsIterator = myChains.entrySet().iterator();
    for (final WeightAware<MethodIncompleteSignature> vertex : myVertexes) {
      final Map.Entry<MethodIncompleteSignature, MethodsChain> chainEntry = chainsIterator.next();
      final MethodIncompleteSignature method = vertex.getUnderlying();
      if (!excludedEdgeNames.contains(method.getName())) {
        initedVertexes.add(vertex);
        final MethodsChain methodsChain = chainEntry.getValue();
        initedChains.put(chainEntry.getKey(), methodsChain);
        if (contextQNames.contains(method.getOwner())) {
          final Integer occurrences = myOccurrencesMap.get(method);
          if (occurrences > bestOccurrences) {
            final PsiMethod oneOfFirst = methodsChain.getOneOfFirst();
            if (oneOfFirst != null && oneOfFirst.hasModifierProperty(PsiModifier.STATIC)) {
              bestTargetMethodChain = methodsChain;
              bestOccurrences = occurrences;
              continue;
            }
            final PsiClass firstQualifierClass = methodsChain.getFirstQualifierClass();
            if (firstQualifierClass != null && (searchService.isSingleton(firstQualifierClass, contextMethodName)
                || contextQNames.contains(firstQualifierClass.getQualifiedName()))) {
              bestTargetMethodChain = methodsChain;
              bestOccurrences = occurrences;
            }
          }
        }
      }
    }
    return new InitResult(initedVertexes, initedChains, bestTargetMethodChain);
  }

  public static class InitResult {
    private final List<WeightAware<MethodIncompleteSignature>> myVertexes;
    private final LinkedHashMap<MethodIncompleteSignature, MethodsChain> myChains;
    private final MethodsChain myCurrentBestTargetChain;

    private InitResult(final List<WeightAware<MethodIncompleteSignature>> vertexes,
                       final LinkedHashMap<MethodIncompleteSignature, MethodsChain> chains,
                       final @Nullable MethodsChain currentBestTargetChain) {
      this.myVertexes = vertexes;
      this.myChains = chains;
      this.myCurrentBestTargetChain = currentBestTargetChain;
    }

    public List<WeightAware<MethodIncompleteSignature>> getVertexes() {
      return myVertexes;
    }

    public LinkedHashMap<MethodIncompleteSignature, MethodsChain> getChains() {
      return myChains;
    }

    @Nullable
    public MethodsChain getCurrentBestTargetChain() {
      return myCurrentBestTargetChain;
    }
  }
}
