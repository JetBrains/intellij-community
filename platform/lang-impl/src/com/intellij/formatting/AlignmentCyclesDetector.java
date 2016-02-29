/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.formatting;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;

import java.util.*;

public class AlignmentCyclesDetector {
  private final int myTotalAlignmentsCount;
  private int myBlockRollbacks;
  
  private LeafBlockWrapper myOffsetResponsibleBlock;
  private int myBeforeTotalSpaces;
  
  private Map<List<LeafBlockWrapper>, Set<Pair<Integer, Integer>>> map = ContainerUtil.newHashMap();

  public AlignmentCyclesDetector(int totalAlignmentsCount) {
    myTotalAlignmentsCount = totalAlignmentsCount;
  }
  
  public void registerOffsetResponsibleBlock(LeafBlockWrapper block) {
    myOffsetResponsibleBlock = block;
    myBeforeTotalSpaces = block.getWhiteSpace().getTotalSpaces();
  }

  public boolean isCycleDetected() {
    return myBlockRollbacks > myTotalAlignmentsCount;
  }

  public void registerBlockRollback(LeafBlockWrapper currentBlock) {
    List<LeafBlockWrapper> pairId = Arrays.asList(currentBlock, myOffsetResponsibleBlock);
    
    Set<Pair<Integer, Integer>> pairs = map.get(pairId);
    if (pairs == null) {
      pairs = new HashSet<>();
      map.put(pairId, pairs);
    }

    int newSpaces = myOffsetResponsibleBlock.getWhiteSpace().getTotalSpaces();
    boolean added = pairs.add(Pair.create(myBeforeTotalSpaces, newSpaces));
    if (added) {
      myBlockRollbacks++;
    }
  }
}
