// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph.Edge;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;

/**
 * @author lambdamix
 */
public final class RichControlFlow {
  public final ControlFlowGraph controlFlow;
  public final DFSTree dfsTree;

  public RichControlFlow(ControlFlowGraph controlFlow, DFSTree dfsTree) {
    this.controlFlow = controlFlow;
    this.dfsTree = dfsTree;
  }

  // Tarjan. Testing flow graph reducibility.
  // Journal of Computer and System Sciences 9.3 (1974): 355-365.
  public boolean reducible() {
    if (dfsTree.back.isEmpty()) {
      return true;
    }
    int size = controlFlow.transitions.length;
    boolean[] loopEnters = dfsTree.loopEnters;
    TIntHashSet[] cycleIncoming = new TIntHashSet[size];
    // really this may be array, since dfs already ensures no duplicates
    TIntArrayList[] nonCycleIncoming = new TIntArrayList[size];
    int[] collapsedTo = new int[size];
    int[] queue = new int[size];
    int top;
    for (int i = 0; i < size; i++) {
      if (loopEnters[i]) {
        cycleIncoming[i] = new TIntHashSet();
      }
      nonCycleIncoming[i] = new TIntArrayList();
      collapsedTo[i] = i;
    }

    // from whom back connections
    for (Edge edge : dfsTree.back) {
      cycleIncoming[edge.to].add(edge.from);
    }
    // from whom ordinary connections
    for (Edge edge : dfsTree.nonBack) {
      nonCycleIncoming[edge.to].add(edge.from);
    }

    for (int w = size - 1; w >= 0 ; w--) {
      top = 0;
      // NB - it is modified later!
      TIntHashSet p = cycleIncoming[w];
      if (p == null) {
        continue;
      }
      TIntIterator it = p.iterator();
      while (it.hasNext()) {
        queue[top++] = it.next();
      }

      while (top > 0) {
        int x = queue[--top];
        TIntArrayList incoming = nonCycleIncoming[x];
        for (int i = 0; i < incoming.size(); i++) {
          int y1 = collapsedTo[incoming.getQuick(i)];
          if (!dfsTree.isDescendant(y1, w)) {
            return false;
          }
          if (y1 != w && p.add(y1)) {
            queue[top++] = y1;
          }
        }
      }

      it = p.iterator();
      while (it.hasNext()) {
        collapsedTo[it.next()] = w;
      }
    }

    return true;
  }
}