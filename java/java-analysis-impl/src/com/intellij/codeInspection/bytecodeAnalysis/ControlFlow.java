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

import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.InsnList;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.*;

import java.util.*;

import static org.jetbrains.org.objectweb.asm.Opcodes.*;

final class cfg {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis.cfg");
  static ControlFlowGraph buildControlFlowGraph(String className, MethodNode methodNode) throws AnalyzerException {
    return new ControlFlowBuilder(className, methodNode).buildCFG();
  }

  static TIntHashSet resultOrigins(String className, MethodNode methodNode) {
    try {
      Frame<SourceValue>[] frames = new Analyzer<SourceValue>(MININAL_ORIGIN_INTERPRETER).analyze(className, methodNode);
      InsnList insns = methodNode.instructions;
      TIntHashSet result = new TIntHashSet();
      for (int i = 0; i < frames.length; i++) {
        AbstractInsnNode insnNode = insns.get(i);
        Frame<SourceValue> frame = frames[i];
        if (frame != null) {
          switch (insnNode.getOpcode()) {
            case ARETURN:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
              for (AbstractInsnNode sourceInsn : frame.pop().insns) {
                result.add(insns.indexOf(sourceInsn));
              }
              break;

            default:
              break;
          }
        }
      }
      return result;
    }
    catch (AnalyzerException e) {
      LOG.error(e);
      throw new RuntimeException();
    }
  }

  static final Interpreter<SourceValue> MININAL_ORIGIN_INTERPRETER = new SourceInterpreter() {
    final SourceValue[] sourceVals = {new SourceValue(1), new SourceValue(2)};

    @Override
    public SourceValue newOperation(AbstractInsnNode insn) {
      SourceValue result = super.newOperation(insn);
      switch (insn.getOpcode()) {
        case ICONST_0:
        case ICONST_1:
        case ACONST_NULL:
        case LDC:
        case NEW:
          return result;
        default:
          return sourceVals[result.getSize() - 1];
      }
    }

    @Override
    public SourceValue unaryOperation(AbstractInsnNode insn, SourceValue value) {
      SourceValue result = super.unaryOperation(insn, value);
      switch (insn.getOpcode()) {
        case CHECKCAST:
        case NEWARRAY:
        case ANEWARRAY:
          return result;
        default:
          return sourceVals[result.getSize() - 1];
      }
    }

    @Override
    public SourceValue binaryOperation(AbstractInsnNode insn, SourceValue value1, SourceValue value2) {
      switch (insn.getOpcode()) {
        case LALOAD:
        case DALOAD:
        case LADD:
        case DADD:
        case LSUB:
        case DSUB:
        case LMUL:
        case DMUL:
        case LDIV:
        case DDIV:
        case LREM:
        case LSHL:
        case LSHR:
        case LUSHR:
        case LAND:
        case LOR:
        case LXOR:
          return sourceVals[1];
        default:
          return sourceVals[0];
      }
    }

    @Override
    public SourceValue ternaryOperation(AbstractInsnNode insn, SourceValue value1, SourceValue value2, SourceValue value3) {
      return sourceVals[0];
    }

    @Override
    public SourceValue copyOperation(AbstractInsnNode insn, SourceValue value) {
      return value;
    }

  };

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
  static DFSTree buildDFSTree(int[][] transitions) {
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
  final int[][] transitions;
  final Set<Edge> errorTransitions;

  ControlFlowGraph(String className, MethodNode methodNode, int[][] transitions, Set<Edge> errorTransitions) {
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
  final TIntArrayList[] transitions;
  final Set<Edge> errorTransitions;

  ControlFlowBuilder(String className, MethodNode methodNode) {
    super(INTERPRETER);
    this.className = className;
    this.methodNode = methodNode;
    transitions = new TIntArrayList[methodNode.instructions.size()];
    for (int i = 0; i < transitions.length; i++) {
      transitions[i] = new TIntArrayList();
    }
    errorTransitions = new HashSet<Edge>();
  }

  final ControlFlowGraph buildCFG() throws AnalyzerException {
    analyze(className, methodNode);
    int[][] resultTransitions = new int[transitions.length][];
    for (int i = 0; i < resultTransitions.length; i++) {
      resultTransitions[i] = transitions[i].toNativeArray();
    }
    return new ControlFlowGraph(className, methodNode, resultTransitions, errorTransitions);
  }

  @Override
  protected final void newControlFlowEdge(int insn, int successor) {
    if (!transitions[insn].contains(successor)) {
      transitions[insn].add(successor);
    }
  }

  @Override
  protected final boolean newControlFlowExceptionEdge(int insn, int successor) {
    if (!transitions[insn].contains(successor)) {
      transitions[insn].add(successor);
      errorTransitions.add(new Edge(insn, successor));
    }
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
