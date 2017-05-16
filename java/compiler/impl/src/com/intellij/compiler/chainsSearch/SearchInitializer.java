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

import java.util.*;

public class SearchInitializer {
  private final ChainCompletionContext myContext;
  private final LinkedList<OccurrencesAware<MethodChain>> myVertices;
  private final LinkedHashMap<MethodIncompleteSignature, MethodChain> myChains;

  public SearchInitializer(SortedSet<OccurrencesAware<MethodIncompleteSignature>> indexValues,
                           ChainCompletionContext context) {
    myContext = context;
    int size = indexValues.size();
    List<MethodChain> chains = new ArrayList<>(size);
    populateFrequentlyUsedMethod(indexValues, chains);
    myVertices = new LinkedList<>();
    myChains = new LinkedHashMap<>(chains.size());
    for (MethodChain chain : chains) {
      MethodIncompleteSignature signature = chain.getHeadSignature();
      myVertices.add(new OccurrencesAware<>(chain, chain.getChainWeight()));
      myChains.put(signature, chain);
    }
  }

  public LinkedList<OccurrencesAware<MethodChain>> getVertices() {
    return myVertices;
  }

  public LinkedHashMap<MethodIncompleteSignature, MethodChain> getChains() {
    return myChains;
  }

  private void populateFrequentlyUsedMethod(SortedSet<OccurrencesAware<MethodIncompleteSignature>> indexValues,
                                            List<MethodChain> chains) {
    int bestOccurrences = -1;
    for (OccurrencesAware<MethodIncompleteSignature> indexValue : indexValues) {
      MethodChain methodChain = MethodChain.create(indexValue.getUnderlying(), indexValue.getOccurrenceCount(), myContext);
      if (methodChain != null) {
        chains.add(methodChain);
        int occurrences = indexValue.getOccurrenceCount();
        if (bestOccurrences == -1) {
          bestOccurrences = occurrences;
        }
        else if (bestOccurrences > occurrences * ChainSearchMagicConstants.FILTER_RATIO) {
          return;
        }
      }
    }
  }
}