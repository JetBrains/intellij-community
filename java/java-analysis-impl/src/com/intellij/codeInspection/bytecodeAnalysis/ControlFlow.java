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

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.Value;

import java.util.*;

import static org.jetbrains.org.objectweb.asm.Opcodes.*;

final class cfg {
  static ControlFlowGraph buildControlFlowGraph(String className, MethodNode methodNode) throws AnalyzerException {
    return new ControlFlowBuilder(className, methodNode).buildCFG();
  }

  static TIntHashSet resultOrigins(String className, MethodNode methodNode) throws AnalyzerException {
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

  static boolean[] leakingParameters(String className, MethodNode methodNode) throws AnalyzerException {
    Frame<ParamsValue>[] frames = new Analyzer<ParamsValue>(new ParametersUsage(methodNode)).analyze(className, methodNode);
    InsnList insns = methodNode.instructions;
    LeakingParametersCollector collector = new LeakingParametersCollector(methodNode);
    for (int i = 0; i < frames.length; i++) {
      AbstractInsnNode insnNode = insns.get(i);
      Frame<ParamsValue> frame = frames[i];
      if (frame != null) {
        switch (insnNode.getType()) {
          case AbstractInsnNode.LABEL:
          case AbstractInsnNode.LINE:
          case AbstractInsnNode.FRAME:
            break;
          default:
            frame.execute(insnNode, collector);
        }
      }
    }
    return collector.leaking;
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

final class ParamsValue implements Value {
  @NotNull final boolean[] params;
  final int size;

  ParamsValue(@NotNull boolean[] params, int size) {
    this.params = params;
    this.size = size;
  }

  @Override
  public int getSize() {
    return size;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    ParamsValue that = (ParamsValue)o;
    return (this.size == that.size && Arrays.equals(this.params, that.params));
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(params) + size;
  }
}

class ParametersUsage extends Interpreter<ParamsValue> {
  final ParamsValue val1;
  final ParamsValue val2;
  int called = -1;
  final int rangeStart;
  final int rangeEnd;
  final int arity;
  final int shift;

  ParametersUsage(MethodNode methodNode) {
    super(ASM5);
    arity = Type.getArgumentTypes(methodNode.desc).length;
    boolean[] emptyParams = new boolean[arity];
    val1 = new ParamsValue(emptyParams, 1);
    val2 = new ParamsValue(emptyParams, 2);

    shift = (methodNode.access & ACC_STATIC) == 0 ? 2 : 1;
    rangeStart = shift;
    rangeEnd = arity + shift;
  }

  @Override
  public ParamsValue newValue(Type type) {
    if (type == null) return val1;
    called++;
    if (type == Type.VOID_TYPE) return null;
    if (called < rangeEnd && rangeStart <= called) {
      boolean[] params = new boolean[arity];
      params[called - shift] = true;
      return type.getSize() == 1 ? new ParamsValue(params, 1) : new ParamsValue(params, 2);
    }
    else {
      return type.getSize() == 1 ? val1 : val2;
    }
  }

  @Override
  public ParamsValue newOperation(final AbstractInsnNode insn) {
    int size;
    switch (insn.getOpcode()) {
      case LCONST_0:
      case LCONST_1:
      case DCONST_0:
      case DCONST_1:
        size = 2;
        break;
      case LDC:
        Object cst = ((LdcInsnNode) insn).cst;
        size = cst instanceof Long || cst instanceof Double ? 2 : 1;
        break;
      case GETSTATIC:
        size = Type.getType(((FieldInsnNode) insn).desc).getSize();
        break;
      default:
        size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public ParamsValue copyOperation(AbstractInsnNode insn, ParamsValue value) {
    return value;
  }

  @Override
  public ParamsValue unaryOperation(AbstractInsnNode insn, ParamsValue value) {
    int size;
    switch (insn.getOpcode()) {
      case CHECKCAST:
        return new ParamsValue(value.params, Type.getObjectType(((TypeInsnNode)insn).desc).getSize());
      case LNEG:
      case DNEG:
      case I2L:
      case I2D:
      case L2D:
      case F2L:
      case F2D:
      case D2L:
        size = 2;
        break;
      case GETFIELD:
        size = Type.getType(((FieldInsnNode) insn).desc).getSize();
        break;
      default:
        size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public ParamsValue binaryOperation(AbstractInsnNode insn, ParamsValue value1, ParamsValue value2) {
    int size;
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
      case DREM:
      case LSHL:
      case LSHR:
      case LUSHR:
      case LAND:
      case LOR:
      case LXOR:
        size = 2;
        break;
      default:
        size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public ParamsValue ternaryOperation(AbstractInsnNode insn, ParamsValue value1, ParamsValue value2, ParamsValue value3) {
    return val1;
  }

  @Override
  public ParamsValue naryOperation(AbstractInsnNode insn, List<? extends ParamsValue> values) {
    int size;
    int opcode = insn.getOpcode();
    if (opcode == MULTIANEWARRAY) {
      size = 1;
    } else {
      String desc = (opcode == INVOKEDYNAMIC) ? ((InvokeDynamicInsnNode) insn).desc : ((MethodInsnNode) insn).desc;
      size = Type.getReturnType(desc).getSize();
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public void returnOperation(AbstractInsnNode insn, ParamsValue value, ParamsValue expected) {}

  @Override
  public ParamsValue merge(ParamsValue v1, ParamsValue v2) {
    if (v1.equals(v2)) return v1;
    boolean[] params = new boolean[arity];
    boolean[] params1 = v1.params;
    boolean[] params2 = v2.params;
    for (int i = 0; i < arity; i++) {
        params[i] = params1[i] || params2[i];
    }
    return new ParamsValue(params, Math.min(v1.size, v2.size));
  }
}

class LeakingParametersCollector extends ParametersUsage {
  final boolean[] leaking;
  LeakingParametersCollector(MethodNode methodNode) {
    super(methodNode);
    leaking = new boolean[arity];
  }

  @Override
  public ParamsValue unaryOperation(AbstractInsnNode insn, ParamsValue value) {
    switch (insn.getOpcode()) {
      case GETFIELD:
      case ARRAYLENGTH:
      case MONITORENTER:
      case INSTANCEOF:
      case IRETURN:
      case ARETURN:
      case IFNONNULL:
      case IFNULL:
      case IFEQ:
      case IFNE:
        boolean[] params = value.params;
        for (int i = 0; i < arity; i++) {
          leaking[i] |= params[i];
        }
        break;
      default:
    }
    return super.unaryOperation(insn, value);
  }

  @Override
  public ParamsValue binaryOperation(AbstractInsnNode insn, ParamsValue value1, ParamsValue value2) {
    switch (insn.getOpcode()) {
      case IALOAD:
      case LALOAD:
      case FALOAD:
      case DALOAD:
      case AALOAD:
      case BALOAD:
      case CALOAD:
      case SALOAD:
      case PUTFIELD:
        boolean[] params = value1.params;
        for (int i = 0; i < arity; i++) {
          leaking[i] |= params[i];
        }
        break;
      default:
    }
    return super.binaryOperation(insn, value1, value2);
  }

  @Override
  public ParamsValue ternaryOperation(AbstractInsnNode insn, ParamsValue value1, ParamsValue value2, ParamsValue value3) {
    switch (insn.getOpcode()) {
      case IASTORE:
      case LASTORE:
      case FASTORE:
      case DASTORE:
      case AASTORE:
      case BASTORE:
      case CASTORE:
      case SASTORE:
        boolean[] params = value1.params;
        for (int i = 0; i < arity; i++) {
          leaking[i] |= params[i];
        }
        break;
      default:
    }
    return super.ternaryOperation(insn, value1, value2, value3);
  }

  @Override
  public ParamsValue naryOperation(AbstractInsnNode insn, List<? extends ParamsValue> values) {
    switch (insn.getOpcode()) {
      case INVOKESTATIC:
      case INVOKESPECIAL:
      case INVOKEVIRTUAL:
      case INVOKEINTERFACE:
        for (ParamsValue value : values) {
          boolean[] params = value.params;
          for (int i = 0; i < arity; i++) {
            leaking[i] |= params[i];
          }
        }
        break;
      default:
    }
    return super.naryOperation(insn, values);
  }
}
