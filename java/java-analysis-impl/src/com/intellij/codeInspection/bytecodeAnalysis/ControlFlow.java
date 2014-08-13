/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph;
import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph.Edge;
import com.intellij.codeInspection.bytecodeAnalysis.asm.FramelessAnalyzer;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;

import java.util.HashSet;
import java.util.Set;

final class cfg {
  static ControlFlowGraph buildControlFlowGraph(String className, MethodNode methodNode) throws AnalyzerException {
    return new ControlFlowBuilder(className, methodNode).buildCFG();
  }

  // Graphs: Theory and Algorithms. by K. Thulasiraman , M. N. S. Swamy (1992)
  // 11.7.2 DFS of a directed graph
  static DFSTree buildDFSTree(int[][] transitions, int edgeCount) {
    HashSet<Edge> nonBack = new HashSet<Edge>();
    HashSet<Edge> back = new HashSet<Edge>();

    boolean[] marked = new boolean[transitions.length];
    boolean[] scanned = new boolean[transitions.length];
    int[] preOrder = new int[transitions.length];
    int[] postOrder = new int[transitions.length];

    int entered = 0;
    int completed = 0;

    boolean[] loopEnters = new boolean[transitions.length];

    // enter 0
    entered ++;
    preOrder[0] = entered;
    marked[0] = true;

    boolean[] stackFlag = new boolean[edgeCount*2 + 1];
    int[] stackFrom = new int[edgeCount*2 + 1];
    int[] stackTo = new int[edgeCount*2 + 1];

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
        completed ++;
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
          entered ++;
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
        } else {
          nonBack.add(new Edge(from, to));
        }
      }
    }

    return new DFSTree(preOrder, postOrder, nonBack, back, loopEnters);
  }

  // Tarjan. Testing flow graph reducibility.
  // Journal of Computer and System Sciences 9.3 (1974): 355-365.
  static boolean reducible(ControlFlowGraph cfg, DFSTree dfs) {
    int size = cfg.transitions.length;
    boolean[] loopEnters = dfs.loopEnters;
    TIntHashSet[] cycleIncomings = new TIntHashSet[size];
    // really this may be array, since dfs already ensures no duplicates
    TIntArrayList[] nonCycleIncomings = new TIntArrayList[size];
    int[] collapsedTo = new int[size];
    int[] queue = new int[size];
    int top;
    for (int i = 0; i < size; i++) {
      if (loopEnters[i]) {
        cycleIncomings[i] = new TIntHashSet();
      }
      nonCycleIncomings[i] = new TIntArrayList();
      collapsedTo[i] = i;
    }

    // from whom back connections
    for (Edge edge : dfs.back) {
      cycleIncomings[edge.to].add(edge.from);
    }
    // from whom ordinary connections
    for (Edge edge : dfs.nonBack) {
      nonCycleIncomings[edge.to].add(edge.from);
    }

    for (int w = size - 1; w >= 0 ; w--) {
      top = 0;
      // NB - it is modified later!
      TIntHashSet p = cycleIncomings[w];
      if (p == null) {
        continue;
      }
      TIntIterator iter = p.iterator();
      while (iter.hasNext()) {
        queue[top++] = iter.next();
      }

      while (top > 0) {
        int x = queue[--top];
        TIntArrayList incoming = nonCycleIncomings[x];
        for (int i = 0; i < incoming.size(); i++) {
          int y1 = collapsedTo[incoming.getQuick(i)];
          if (!dfs.isDescendant(y1, w)) {
            return false;
          }
          if (y1 != w && p.add(y1)) {
            queue[top++] = y1;
          }
        }
      }

      iter = p.iterator();
      while (iter.hasNext()) {
        collapsedTo[iter.next()] = w;
      }
    }

    return true;
  }

}

final class RichControlFlow {
  final ControlFlowGraph controlFlow;
  final DFSTree dfsTree;

  RichControlFlow(ControlFlowGraph controlFlow, DFSTree dfsTree) {
    this.controlFlow = controlFlow;
    this.dfsTree = dfsTree;
  }
}

final class ControlFlowBuilder extends FramelessAnalyzer {
  final String className;
  final MethodNode methodNode;
  final TIntArrayList[] transitions;
  final Set<ControlFlowGraph.Edge> errorTransitions;
  private final boolean[] errors;
  private int edgeCount;

  ControlFlowBuilder(String className, MethodNode methodNode) {
    this.className = className;
    this.methodNode = methodNode;
    transitions = new TIntArrayList[methodNode.instructions.size()];
    errors = new boolean[methodNode.instructions.size()];
    for (int i = 0; i < transitions.length; i++) {
      transitions[i] = new TIntArrayList();
    }
    errorTransitions = new HashSet<Edge>();
  }

  final ControlFlowGraph buildCFG() throws AnalyzerException {
    if ((methodNode.access & (ACC_ABSTRACT | ACC_NATIVE)) == 0) {
      analyze(methodNode);
    }
    int[][] resultTransitions = new int[transitions.length][];
    for (int i = 0; i < resultTransitions.length; i++) {
      resultTransitions[i] = transitions[i].toNativeArray();
    }
    return new ControlFlowGraph(className, methodNode, resultTransitions, edgeCount, errors, errorTransitions);
  }

  @Override
  protected final void newControlFlowEdge(int insn, int successor) {
    if (!transitions[insn].contains(successor)) {
      transitions[insn].add(successor);
      edgeCount++;
    }
  }

  @Override
  protected final boolean newControlFlowExceptionEdge(int insn, int successor) {
    if (!transitions[insn].contains(successor)) {
      transitions[insn].add(successor);
      edgeCount++;
      errorTransitions.add(new Edge(insn, successor));
      errors[successor] = true;
    }
    return true;
  }
}

final class DFSTree {
  final int[] preOrder, postOrder;
  final Set<Edge> nonBack, back;
  final boolean[] loopEnters;

  DFSTree(int[] preOrder,
          int[] postOrder,
          Set<Edge> nonBack,
          Set<Edge> back,
          boolean[] loopEnters) {
    this.preOrder = preOrder;
    this.postOrder = postOrder;
    this.nonBack = nonBack;
    this.back = back;
    this.loopEnters = loopEnters;
  }

  final boolean isDescendant(int child, int parent) {
    return preOrder[parent] <= preOrder[child] && postOrder[child] <= postOrder[parent];
  }
}

