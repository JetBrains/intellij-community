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

import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.Analyzer;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;

import java.util.*;

final class cfg {
  static ControlFlowGraph buildControlFlowGraph(String className, MethodNode methodNode) {
    try {
      return new ControlFlowBuilder(className, methodNode).buildCFG();
    } catch (AnalyzerException e) {
      throw new RuntimeException();
    }
  }

  private interface Action {}
  private static class MarkScanned implements Action {
    final int node;
    private MarkScanned(int node) {
      this.node = node;
    }
  }
  private static class ExamineEdge implements Action {
    final int from;
    final int to;

    private ExamineEdge(int from, int to) {
      this.from = from;
      this.to = to;
    }
  }

  // Graphs: Theory and Algorithms. by K. Thulasiraman , M. N. S. Swamy (1992)
  // 11.7.2 DFS of a directed graph
  static DFSTree buildDFSTree(List<Integer>[] transitions) {
    Set<Edge> tree = new HashSet<Edge>();
    Set<Edge> forward = new HashSet<Edge>();
    Set<Edge> back = new HashSet<Edge>();
    Set<Edge> cross = new HashSet<Edge>();

    boolean[] marked = new boolean[transitions.length];
    boolean[] scanned = new boolean[transitions.length];
    int[] preOrder = new int[transitions.length];
    int[] postOrder = new int[transitions.length];

    int entered = 0;
    int completed = 0;

    Deque<Action> stack = new LinkedList<Action>();
    Set<Integer> loopEnters = new HashSet<Integer>();

    // enter 0
    entered ++;
    preOrder[0] = entered;
    marked[0] = true;
    stack.push(new MarkScanned(0));
    for (int to : transitions[0]) {
      stack.push(new ExamineEdge(0, to));
    }

    while (!stack.isEmpty()) {
      Action action = stack.pop();
      if (action instanceof MarkScanned) {
        MarkScanned markScannedAction = (MarkScanned) action;
        completed ++;
        postOrder[markScannedAction.node] = completed;
        scanned[markScannedAction.node] = true;
      }
      else {
        ExamineEdge examineEdgeAction = (ExamineEdge) action;
        int from = examineEdgeAction.from;
        int to = examineEdgeAction.to;
        if (!marked[to]) {
          tree.add(new Edge(from, to));
          // enter to
          entered ++;
          preOrder[to] = entered;
          marked[to] = true;
          stack.push(new MarkScanned(to));
          for (int to1 : transitions[to]) {
            stack.push(new ExamineEdge(to, to1));
          }
        }
        else if (preOrder[to] > preOrder[from]) {
          forward.add(new Edge(from, to));
        }
        else if (preOrder[to] < preOrder[from] && !scanned[to]) {
          back.add(new Edge(from, to));
          loopEnters.add(to);
        } else {
          cross.add(new Edge(from, to));
        }
      }
    }

    return new DFSTree(preOrder, postOrder, tree, forward, back, cross, loopEnters);
  }

  // Tarjan. Testing flow graph reducibility.
  // Journal of Computer and System Sciences 9.3 (1974): 355-365.
  static boolean reducible(ControlFlowGraph cfg, DFSTree dfs) {
    int size = cfg.transitions.length;
    HashSet<Integer>[] cycles = new HashSet[size];
    HashSet<Integer>[] nonCycles = new HashSet[size];
    int[] collapsedTo = new int[size];
    for (int i = 0; i < size; i++) {
      cycles[i] = new HashSet<Integer>();
      nonCycles[i] = new HashSet<Integer>();
      collapsedTo[i] = i;
    }

    for (Edge edge : dfs.back) {
      cycles[edge.to].add(edge.from);
    }
    for (Edge edge : dfs.tree) {
      nonCycles[edge.to].add(edge.from);
    }
    for (Edge edge : dfs.forward) {
      nonCycles[edge.to].add(edge.from);
    }
    for (Edge edge : dfs.cross) {
      nonCycles[edge.to].add(edge.from);
    }

    for (int w = size - 1; w >= 0 ; w--) {
      HashSet<Integer> p = new HashSet<Integer>(cycles[w]);
      Queue<Integer> queue = new LinkedList<Integer>(cycles[w]);

      while (!queue.isEmpty()) {
        int x = queue.remove();
        for (int y : nonCycles[x]) {
          int y1 = collapsedTo[y];
          if (!dfs.isDescendant(y1, w)) {
            return false;
          }
          if (y1 != w && p.add(y1)) {
            queue.add(y1);
          }
        }
      }

      for (int x : p) {
        collapsedTo[x] = w;
      }
    }

    return true;
  }

}

final class Edge {
  final int from, to;

  Edge(int from, int to) {
    this.from = from;
    this.to = to;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Edge)) {
      return false;
    }
    Edge edge = (Edge) o;
    return from == edge.from && to == edge.to;
  }

  @Override
  public int hashCode() {
    return 31 * from + to;
  }

  @Override
  public String toString() {
    return "(" + from + "," + to + ")";
  }
}

final class ControlFlowGraph {
  final String className;
  final MethodNode methodNode;
  final List<Integer>[] transitions;
  final Set<Edge> errorTransitions;

  ControlFlowGraph(String className, MethodNode methodNode, List<Integer>[] transitions, Set<Edge> errorTransitions) {
    this.className = className;
    this.methodNode = methodNode;
    this.transitions = transitions;
    this.errorTransitions = errorTransitions;
  }

  @Override
  public String toString() {
    return "CFG(" +
           Arrays.toString(transitions) + "," +
           errorTransitions +
           ')';
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

final class ControlFlowBuilder extends Analyzer<BasicValue> {
  static final BasicInterpreter INTERPRETER = new BasicInterpreter();
  final String className;
  final MethodNode methodNode;
  final LinkedList<Integer>[] transitions;
  final Set<Edge> errorTransitions;

  ControlFlowBuilder(String className, MethodNode methodNode) {
    super(INTERPRETER);
    this.className = className;
    this.methodNode = methodNode;
    transitions = new LinkedList[methodNode.instructions.size()];
    for (int i = 0; i < transitions.length; i++) {
      transitions[i] = new LinkedList<Integer>();
    }
    errorTransitions = new HashSet<Edge>();
  }

  final ControlFlowGraph buildCFG() throws AnalyzerException {
    analyze(className, methodNode);
    return new ControlFlowGraph(className, methodNode, transitions, errorTransitions);
  }

  @Override
  protected final void newControlFlowEdge(int insn, int successor) {
    transitions[insn].addFirst(successor);
  }

  @Override
  protected final boolean newControlFlowExceptionEdge(int insn, int successor) {
    transitions[insn].addFirst(successor);
    errorTransitions.add(new Edge(insn, successor));
    return true;
  }
}

final class DFSTree {
  final int[] preOrder, postOrder;
  final Set<Edge> tree, forward, back, cross;
  final Set<Integer> loopEnters;

  DFSTree(int[] preOrder,
          int[] postOrder,
          Set<Edge> tree,
          Set<Edge> forward,
          Set<Edge> back,
          Set<Edge> cross,
          Set<Integer> loopEnters) {
    this.preOrder = preOrder;
    this.postOrder = postOrder;
    this.tree = tree;
    this.forward = forward;
    this.back = back;
    this.cross = cross;
    this.loopEnters = loopEnters;
  }

  final boolean isDescendant(int child, int parent) {
    return preOrder[parent] <= preOrder[child] && postOrder[child] <= postOrder[parent];
  }
}
