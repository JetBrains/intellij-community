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
import org.jetbrains.org.objectweb.asm.Opcodes;
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

final class ControlFlowBuilder extends CfgAnalyzer {
  final String className;
  final MethodNode methodNode;
  final TIntArrayList[] transitions;
  final Set<Edge> errorTransitions;

  ControlFlowBuilder(String className, MethodNode methodNode) {
    this.className = className;
    this.methodNode = methodNode;
    transitions = new TIntArrayList[methodNode.instructions.size()];
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

/**
 * Specialized lite version of {@link org.jetbrains.org.objectweb.asm.tree.analysis.Analyzer}.
 * Calculation of fix-point of frames is removed, since frames are not needed to build control flow graph.
 * So, the main point here is handling of subroutines (jsr) and try-catch-finally blocks.
 */
class CfgAnalyzer implements Opcodes {
  static class Subroutine {

    LabelNode start;

    boolean[] access;

    List<JumpInsnNode> callers;

    private Subroutine() {
    }

    Subroutine(final LabelNode start, final int maxLocals,
               final JumpInsnNode caller) {
      this.start = start;
      this.access = new boolean[maxLocals];
      this.callers = new ArrayList<JumpInsnNode>();
      callers.add(caller);
    }

    public Subroutine copy() {
      Subroutine result = new Subroutine();
      result.start = start;
      result.access = new boolean[access.length];
      System.arraycopy(access, 0, result.access, 0, access.length);
      result.callers = new ArrayList<JumpInsnNode>(callers);
      return result;
    }

    public boolean merge(final Subroutine subroutine) throws AnalyzerException {
      boolean changes = false;
      for (int i = 0; i < access.length; ++i) {
        if (subroutine.access[i] && !access[i]) {
          access[i] = true;
          changes = true;
        }
      }
      if (subroutine.start == start) {
        for (int i = 0; i < subroutine.callers.size(); ++i) {
          JumpInsnNode caller = subroutine.callers.get(i);
          if (!callers.contains(caller)) {
            callers.add(caller);
            changes = true;
          }
        }
      }
      return changes;
    }
  }
  private int n;
  private InsnList insns;
  private List<TryCatchBlockNode>[] handlers;
  private Subroutine[] subroutines;
  private boolean[] wasQueued;
  private boolean[] queued;
  private int[] queue;
  private int top;

  public void analyze(final MethodNode m) throws AnalyzerException {
    n = m.instructions.size();
    insns = m.instructions;
    handlers = (List<TryCatchBlockNode>[]) new List<?>[n];
    subroutines = new Subroutine[n];
    queued = new boolean[n];
    wasQueued = new boolean[n];
    queue = new int[n];
    top = 0;

    // computes exception handlers for each instruction
    for (int i = 0; i < m.tryCatchBlocks.size(); ++i) {
      TryCatchBlockNode tcb = m.tryCatchBlocks.get(i);
      int begin = insns.indexOf(tcb.start);
      int end = insns.indexOf(tcb.end);
      for (int j = begin; j < end; ++j) {
        List<TryCatchBlockNode> insnHandlers = handlers[j];
        if (insnHandlers == null) {
          insnHandlers = new ArrayList<TryCatchBlockNode>();
          handlers[j] = insnHandlers;
        }
        insnHandlers.add(tcb);
      }
    }

    // computes the subroutine for each instruction:
    Subroutine main = new Subroutine(null, m.maxLocals, null);
    List<AbstractInsnNode> subroutineCalls = new ArrayList<AbstractInsnNode>();
    Map<LabelNode, Subroutine> subroutineHeads = new HashMap<LabelNode, Subroutine>();

    findSubroutine(0, main, subroutineCalls);
    while (!subroutineCalls.isEmpty()) {
      JumpInsnNode jsr = (JumpInsnNode) subroutineCalls.remove(0);
      Subroutine sub = subroutineHeads.get(jsr.label);
      if (sub == null) {
        sub = new Subroutine(jsr.label, m.maxLocals, jsr);
        subroutineHeads.put(jsr.label, sub);
        findSubroutine(insns.indexOf(jsr.label), sub, subroutineCalls);
      } else {
        sub.callers.add(jsr);
      }
    }
    for (int i = 0; i < n; ++i) {
      if (subroutines[i] != null && subroutines[i].start == null) {
        subroutines[i] = null;
      }
    }

    merge(0, null);
    // control flow analysis
    while (top > 0) {
      int insn = queue[--top];
      Subroutine subroutine = subroutines[insn];
      queued[insn] = false;

      AbstractInsnNode insnNode = null;
      try {
        insnNode = m.instructions.get(insn);
        int insnOpcode = insnNode.getOpcode();
        int insnType = insnNode.getType();

        if (insnType == AbstractInsnNode.LABEL || insnType == AbstractInsnNode.LINE || insnType == AbstractInsnNode.FRAME) {
          merge(insn + 1, subroutine);
          newControlFlowEdge(insn, insn + 1);
        } else {
          subroutine = subroutine == null ? null : subroutine.copy();

          if (insnNode instanceof JumpInsnNode) {
            JumpInsnNode j = (JumpInsnNode) insnNode;
            if (insnOpcode != GOTO && insnOpcode != JSR) {
              merge(insn + 1, subroutine);
              newControlFlowEdge(insn, insn + 1);
            }
            int jump = insns.indexOf(j.label);
            if (insnOpcode == JSR) {
              merge(jump, new Subroutine(j.label, m.maxLocals, j));
            } else {
              merge(jump, subroutine);
            }
            newControlFlowEdge(insn, jump);
          } else if (insnNode instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode lsi = (LookupSwitchInsnNode) insnNode;
            int jump = insns.indexOf(lsi.dflt);
            merge(jump, subroutine);
            newControlFlowEdge(insn, jump);
            for (int j = 0; j < lsi.labels.size(); ++j) {
              LabelNode label = lsi.labels.get(j);
              jump = insns.indexOf(label);
              merge(jump, subroutine);
              newControlFlowEdge(insn, jump);
            }
          } else if (insnNode instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode tsi = (TableSwitchInsnNode) insnNode;
            int jump = insns.indexOf(tsi.dflt);
            merge(jump, subroutine);
            newControlFlowEdge(insn, jump);
            for (int j = 0; j < tsi.labels.size(); ++j) {
              LabelNode label = tsi.labels.get(j);
              jump = insns.indexOf(label);
              merge(jump, subroutine);
              newControlFlowEdge(insn, jump);
            }
          } else if (insnOpcode == RET) {
            if (subroutine == null) {
              throw new AnalyzerException(insnNode, "RET instruction outside of a sub routine");
            }
            for (int i = 0; i < subroutine.callers.size(); ++i) {
              JumpInsnNode caller = subroutine.callers.get(i);
              int call = insns.indexOf(caller);
              if (wasQueued[call]) {
                merge(call + 1, subroutines[call], subroutine.access);
                newControlFlowEdge(insn, call + 1);
              }
            }
          } else if (insnOpcode != ATHROW && (insnOpcode < IRETURN || insnOpcode > RETURN)) {
            if (subroutine != null) {
              if (insnNode instanceof VarInsnNode) {
                int var = ((VarInsnNode) insnNode).var;
                subroutine.access[var] = true;
                if (insnOpcode == LLOAD || insnOpcode == DLOAD
                    || insnOpcode == LSTORE
                    || insnOpcode == DSTORE) {
                  subroutine.access[var + 1] = true;
                }
              } else if (insnNode instanceof IincInsnNode) {
                int var = ((IincInsnNode) insnNode).var;
                subroutine.access[var] = true;
              }
            }
            merge(insn + 1, subroutine);
            newControlFlowEdge(insn, insn + 1);
          }
        }

        List<TryCatchBlockNode> insnHandlers = handlers[insn];
        if (insnHandlers != null) {
          for (TryCatchBlockNode tcb : insnHandlers) {
            newControlFlowExceptionEdge(insn, tcb);
            merge(insns.indexOf(tcb.handler), subroutine);
          }
        }
      } catch (AnalyzerException e) {
        throw new AnalyzerException(e.node, "Error at instruction "
                                            + insn + ": " + e.getMessage(), e);
      } catch (Exception e) {
        throw new AnalyzerException(insnNode, "Error at instruction "
                                              + insn + ": " + e.getMessage(), e);
      }
    }
  }

  private void findSubroutine(int insn, final Subroutine sub,
                              final List<AbstractInsnNode> calls) throws AnalyzerException {
    while (true) {
      if (insn < 0 || insn >= n) {
        throw new AnalyzerException(null, "Execution can fall off end of the code");
      }
      if (subroutines[insn] != null) {
        return;
      }
      subroutines[insn] = sub.copy();
      AbstractInsnNode node = insns.get(insn);

      // calls findSubroutine recursively on normal successors
      if (node instanceof JumpInsnNode) {
        if (node.getOpcode() == JSR) {
          // do not follow a JSR, it leads to another subroutine!
          calls.add(node);
        } else {
          JumpInsnNode jnode = (JumpInsnNode) node;
          findSubroutine(insns.indexOf(jnode.label), sub, calls);
        }
      } else if (node instanceof TableSwitchInsnNode) {
        TableSwitchInsnNode tsnode = (TableSwitchInsnNode) node;
        findSubroutine(insns.indexOf(tsnode.dflt), sub, calls);
        for (int i = tsnode.labels.size() - 1; i >= 0; --i) {
          LabelNode l = tsnode.labels.get(i);
          findSubroutine(insns.indexOf(l), sub, calls);
        }
      } else if (node instanceof LookupSwitchInsnNode) {
        LookupSwitchInsnNode lsnode = (LookupSwitchInsnNode) node;
        findSubroutine(insns.indexOf(lsnode.dflt), sub, calls);
        for (int i = lsnode.labels.size() - 1; i >= 0; --i) {
          LabelNode l = lsnode.labels.get(i);
          findSubroutine(insns.indexOf(l), sub, calls);
        }
      }

      // calls findSubroutine recursively on exception handler successors
      List<TryCatchBlockNode> insnHandlers = handlers[insn];
      if (insnHandlers != null) {
        for (int i = 0; i < insnHandlers.size(); ++i) {
          TryCatchBlockNode tcb = insnHandlers.get(i);
          findSubroutine(insns.indexOf(tcb.handler), sub, calls);
        }
      }

      // if insn does not falls through to the next instruction, return.
      switch (node.getOpcode()) {
        case GOTO:
        case RET:
        case TABLESWITCH:
        case LOOKUPSWITCH:
        case IRETURN:
        case LRETURN:
        case FRETURN:
        case DRETURN:
        case ARETURN:
        case RETURN:
        case ATHROW:
          return;
      }
      insn++;
    }
  }

  protected void newControlFlowEdge(final int insn, final int successor) {}

  protected boolean newControlFlowExceptionEdge(final int insn,
                                                final int successor) {
    return true;
  }

  protected boolean newControlFlowExceptionEdge(final int insn,
                                                final TryCatchBlockNode tcb) {
    return newControlFlowExceptionEdge(insn, insns.indexOf(tcb.handler));
  }

  // -------------------------------------------------------------------------

  private void merge(final int insn, final Subroutine subroutine) throws AnalyzerException {
    Subroutine oldSubroutine = subroutines[insn];
    boolean changes = false;

    if (!wasQueued[insn]) {
      wasQueued[insn] = true;
      changes = true;
    }

    if (oldSubroutine == null) {
      if (subroutine != null) {
        subroutines[insn] = subroutine.copy();
        changes = true;
      }
    } else {
      if (subroutine != null) {
        changes |= oldSubroutine.merge(subroutine);
      }
    }
    if (changes && !queued[insn]) {
      queued[insn] = true;
      queue[top++] = insn;
    }
  }

  private void merge(final int insn, final Subroutine subroutineBeforeJSR, final boolean[] access) throws AnalyzerException {
    Subroutine oldSubroutine = subroutines[insn];
    boolean changes = false;

    if (!wasQueued[insn]) {
      wasQueued[insn] = true;
      changes = true;
    }

    if (oldSubroutine != null && subroutineBeforeJSR != null) {
      changes |= oldSubroutine.merge(subroutineBeforeJSR);
    }
    if (changes && !queued[insn]) {
      queued[insn] = true;
      queue[top++] = insn;
    }
  }
}

