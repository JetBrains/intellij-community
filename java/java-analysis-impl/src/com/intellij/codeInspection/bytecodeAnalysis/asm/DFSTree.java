// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public final class DFSTree {
  public final int[] preOrder, postOrder;
  private final LongOpenHashSet nonBack, back;
  public final boolean[] loopEnters;
  
  public interface EdgeVisitor {
    void visit(int from, int to);
  }

  DFSTree(int[] preOrder, int[] postOrder, LongOpenHashSet nonBack, LongOpenHashSet back, boolean[] loopEnters) {
    this.preOrder = preOrder;
    this.postOrder = postOrder;
    this.nonBack = nonBack;
    this.back = back;
    this.loopEnters = loopEnters;
  }
  
  public boolean isBackEmpty() {
    return back.isEmpty();
  }

  public boolean isDescendant(int child, int parent) {
    return preOrder[parent] <= preOrder[child] && postOrder[child] <= postOrder[parent];
  }
  
  public void iterateBack(EdgeVisitor visitor) {
    iterate(back, visitor);
  }

  public void iterateNonBack(EdgeVisitor visitor) {
    iterate(nonBack, visitor);
  }

  private static void iterate(LongOpenHashSet set, EdgeVisitor visitor) {
    set.forEach(packed -> visitor.visit((int)(packed >>> 32), (int)(packed)));
  }

  private static void putEdge(LongOpenHashSet set, int from, int to) {
    set.add(from * 0x1_0000_0000L + to);
  }

  // "Graphs: Theory and Algorithms" (ISBN 0471513563), 11.7.2 DFS of a directed graph
  public static DFSTree build(int[][] transitions, int edgeCount) {
    LongOpenHashSet nonBack = new LongOpenHashSet();
    LongOpenHashSet back = new LongOpenHashSet();

    boolean[] marked = new boolean[transitions.length];
    boolean[] scanned = new boolean[transitions.length];
    int[] preOrder = new int[transitions.length];
    int[] postOrder = new int[transitions.length];

    int entered = 0;
    int completed = 0;

    boolean[] loopEnters = new boolean[transitions.length];

    // enter 0
    entered++;
    preOrder[0] = entered;
    marked[0] = true;

    boolean[] stackFlag = new boolean[edgeCount * 2 + 1];
    int[] stackFrom = new int[edgeCount * 2 + 1];
    int[] stackTo = new int[edgeCount * 2 + 1];

    int top = 0;

    // stack.push(new MarkScanned(0));
    stackFlag[top] = true;
    stackTo[top] = 0;
    top++;

    for (int to : transitions[0]) {
      //stack.push(new ExamineEdge(0, to));
      stackFlag[top] = false;
      stackFrom[top] = 0;
      stackTo[top] = to;
      top++;
    }

    while (top > 0) {
      top--;
      //Action action = stack.pop();
      // markScanned
      if (stackFlag[top]) {
        completed++;
        postOrder[stackTo[top]] = completed;
        scanned[stackTo[top]] = true;
      }
      else {
        //ExamineEdge examineEdgeAction = (ExamineEdge) action;
        int from = stackFrom[top];
        int to = stackTo[top];
        if (!marked[to]) {
          putEdge(nonBack, from, to);
          // enter to
          entered++;
          preOrder[to] = entered;
          marked[to] = true;

          //stack.push(new MarkScanned(to));
          stackFlag[top] = true;
          stackTo[top] = to;
          top++;

          for (int to1 : transitions[to]) {
            //stack.push(new ExamineEdge(to, to1));
            stackFlag[top] = false;
            stackFrom[top] = to;
            stackTo[top] = to1;
            top++;
          }
        }
        else if (preOrder[to] > preOrder[from]) {
          putEdge(nonBack, from, to);
        }
        else if (preOrder[to] < preOrder[from] && !scanned[to]) {
          putEdge(back, from, to);
          loopEnters[to] = true;
        }
        else {
          putEdge(nonBack, from, to);
        }
      }
    }

    return new DFSTree(preOrder, postOrder, nonBack, back, loopEnters);
  }
}