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
package com.intellij.compiler.chainsSearch;

import com.intellij.compiler.backwardRefs.MethodIncompleteSignature;
import com.intellij.compiler.chainsSearch.context.ChainCompletionContext;
import com.intellij.openapi.util.Pair;

import java.util.*;

public class SearchInitializer {
  private final static int CHAIN_SEARCH_MAGIC_RATIO = 10;

  private final LinkedHashMap<MethodIncompleteSignature, Pair<MethodsChain, Integer>> myChains;
  private final ChainCompletionContext myContext;

  public SearchInitializer(SortedSet<OccurrencesAware<MethodIncompleteSignature>> indexValues,
                           ChainCompletionContext context) {
    myContext = context;
    int size = indexValues.size();
    myChains = new LinkedHashMap<>(size);
    add(indexValues);
  }

  private void add(Collection<OccurrencesAware<MethodIncompleteSignature>> indexValues) {
    int bestOccurrences = -1;
    for (OccurrencesAware<MethodIncompleteSignature> indexValue : indexValues) {
      if (add(indexValue)) {
        int occurrences = indexValue.getOccurrences();
        if (bestOccurrences == -1) {
          bestOccurrences = occurrences;
        }
        else if (bestOccurrences > occurrences * CHAIN_SEARCH_MAGIC_RATIO) {
          return;
        }
      }
    }
  }

  private boolean add(OccurrencesAware<MethodIncompleteSignature> indexValue) {
    MethodIncompleteSignature methodInvocation = indexValue.getUnderlying();
    int occurrences = indexValue.getOccurrences();
    MethodsChain methodsChain = MethodsChain.create(indexValue.getUnderlying(), occurrences, myContext);
    if (methodsChain != null) {
      myChains.put(methodInvocation, Pair.create(methodsChain, occurrences));
      return true;
    }
    return false;
  }

  public InitResult init(Set<String> excludedEdgeNames) {
    int size = myChains.size();
    List<OccurrencesAware<MethodIncompleteSignature>> initedVertexes = new ArrayList<>(size);
    LinkedHashMap<MethodIncompleteSignature, MethodsChain> initedChains =
      new LinkedHashMap<>(size);
    for (Map.Entry<MethodIncompleteSignature, Pair<MethodsChain, Integer>> entry : myChains.entrySet()) {
      MethodIncompleteSignature signature = entry.getKey();
      if (!excludedEdgeNames.contains(signature.getName())) {
        initedVertexes.add(new OccurrencesAware<>(entry.getKey(), entry.getValue().getSecond()));
        MethodsChain methodsChain = entry.getValue().getFirst();
        initedChains.put(signature, methodsChain);
      }
    }
    return new InitResult(initedVertexes, initedChains);
  }

  public static class InitResult {
    private final List<OccurrencesAware<MethodIncompleteSignature>> myVertexes;
    private final LinkedHashMap<MethodIncompleteSignature, MethodsChain> myChains;

    private InitResult(List<OccurrencesAware<MethodIncompleteSignature>> vertexes,
                       LinkedHashMap<MethodIncompleteSignature, MethodsChain> chains) {
      myVertexes = vertexes;
      myChains = chains;
    }

    public List<OccurrencesAware<MethodIncompleteSignature>> getVertices() {
      return myVertexes;
    }

    public LinkedHashMap<MethodIncompleteSignature, MethodsChain> getChains() {
      return myChains;
    }
  }
}