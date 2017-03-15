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
package com.intellij.compiler.classFilesIndex.chainsSearch;

import com.intellij.compiler.classFilesIndex.chainsSearch.context.ChainCompletionContext;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiMethod;
import com.intellij.compiler.classFilesIndex.impl.MethodIncompleteSignature;

import java.util.*;

public class SearchInitializer {
  private final static int CHAIN_SEARCH_MAGIC_RATIO = 12;

  private final LinkedHashMap<MethodIncompleteSignature, Pair<MethodsChain, Integer>> myChains;
  private final ChainCompletionContext myContext;

  public SearchInitializer(final SortedSet<OccurrencesAware<MethodIncompleteSignature>> indexValues,
                           final String targetQName,
                           final Set<String> excludedParamsTypesQNames,
                           final ChainCompletionContext context) {
    myContext = context;
    final int size = indexValues.size();
    myChains = new LinkedHashMap<>(size);
    add(indexValues, MethodChainsSearchUtil.joinToHashSet(excludedParamsTypesQNames, targetQName));
  }

  private void add(final Collection<OccurrencesAware<MethodIncompleteSignature>> indexValues,
                   final Set<String> excludedParamsTypesQNames) {
    int bestOccurrences = -1;
    for (final OccurrencesAware<MethodIncompleteSignature> indexValue : indexValues) {
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

  private boolean add(final OccurrencesAware<MethodIncompleteSignature> indexValue, final Set<String> excludedParamsTypesQNames) {
    final MethodIncompleteSignature methodInvocation = indexValue.getUnderlying();
    final PsiMethod[] psiMethods = myContext.resolveNotDeprecated(methodInvocation);
    if (psiMethods.length != 0 && MethodChainsSearchUtil.checkParametersForTypesQNames(psiMethods, excludedParamsTypesQNames)) {
      final int occurrences = indexValue.getOccurrences();
      final MethodsChain methodsChain = new MethodsChain(psiMethods, occurrences, indexValue.getUnderlying().getOwner());
      myChains.put(methodInvocation, Pair.create(methodsChain, occurrences));
      return true;
    }
    return false;
  }

  public InitResult init(final Set<String> excludedEdgeNames) {
    final int size = myChains.size();
    final List<OccurrencesAware<MethodIncompleteSignature>> initedVertexes = new ArrayList<>(size);
    final LinkedHashMap<MethodIncompleteSignature, MethodsChain> initedChains =
      new LinkedHashMap<>(size);
    for (final Map.Entry<MethodIncompleteSignature, Pair<MethodsChain, Integer>> entry : myChains.entrySet()) {
      final MethodIncompleteSignature signature = entry.getKey();
      if (!excludedEdgeNames.contains(signature.getName())) {
        initedVertexes.add(new OccurrencesAware<>(entry.getKey(), entry.getValue().getSecond()));
        final MethodsChain methodsChain = entry.getValue().getFirst();
        initedChains.put(signature, methodsChain);
      }
    }
    return new InitResult(initedVertexes, initedChains);
  }

  public static class InitResult {
    private final List<OccurrencesAware<MethodIncompleteSignature>> myVertexes;
    private final LinkedHashMap<MethodIncompleteSignature, MethodsChain> myChains;

    private InitResult(final List<OccurrencesAware<MethodIncompleteSignature>> vertexes,
                       final LinkedHashMap<MethodIncompleteSignature, MethodsChain> chains) {
      myVertexes = vertexes;
      myChains = chains;
    }

    public List<OccurrencesAware<MethodIncompleteSignature>> getVertexes() {
      return myVertexes;
    }

    public LinkedHashMap<MethodIncompleteSignature, MethodsChain> getChains() {
      return myChains;
    }
  }
}