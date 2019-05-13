// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph.Edge;

import java.util.HashSet;
import java.util.Set;

/**
 * @author lambdamix
 */
public final class DFSTree {
  public final int[] preOrder, postOrder;
  public final Set<Edge> nonBack, back;
  public final boolean[] loopEnters;

  DFSTree(int[] preOrder, int[] postOrder, Set<Edge> nonBack, Set<Edge> back, boolean[] loopEnters) {
    this.preOrder = preOrder;
    this.postOrder = postOrder;
    this.nonBack = nonBack;
    this.back = back;
    this.loopEnters = loopEnters;
  }

  public final boolean isDescendant(int child, int parent) {
    return preOrder[parent] <= preOrder[child] && postOrder[child] <= postOrder[parent];
  }

  // "Graphs: Theory and Algorithms" (ISBN 0471513563), 11.7.2 DFS of a directed graph
  public static DFSTree build(int[][] transitions, int edgeCount) {
    HashSet<Edge> nonBack = new HashSet<>();
    HashSet<Edge> back = new HashSet<>();

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
          nonBack.add(new Edge(from, to));
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
          nonBack.add(new Edge(from, to));
        }
        else if (preOrder[to] < preOrder[from] && !scanned[to]) {
          back.add(new Edge(from, to));
          loopEnters[to] = true;
        }
        else {
          nonBack.add(new Edge(from, to));
        }
      }
    }

    return new DFSTree(preOrder, postOrder, nonBack, back, loopEnters);
  }
}