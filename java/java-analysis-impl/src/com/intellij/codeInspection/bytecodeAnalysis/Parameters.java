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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph.Edge;
import com.intellij.codeInspection.bytecodeAnalysis.asm.RichControlFlow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.JumpInsnNode;
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode;
import org.jetbrains.org.objectweb.asm.tree.TypeInsnNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.*;
import static com.intellij.codeInspection.bytecodeAnalysis.Direction.In;
import static com.intellij.codeInspection.bytecodeAnalysis.PResults.*;
import static org.jetbrains.org.objectweb.asm.Opcodes.*;

abstract class PResults {
  // SoP = sum of products
  static Set<Set<Key>> join(Set<Set<Key>> sop1, Set<Set<Key>> sop2) {
    Set<Set<Key>> sop = new HashSet<>();
    sop.addAll(sop1);
    sop.addAll(sop2);
    return sop;
  }

  static Set<Set<Key>> meet(Set<Set<Key>> sop1, Set<Set<Key>> sop2) {
    Set<Set<Key>> sop = new HashSet<>();
    for (Set<Key> prod1 : sop1) {
      for (Set<Key> prod2 : sop2) {
        Set<Key> prod = new HashSet<>();
        prod.addAll(prod1);
        prod.addAll(prod2);
        sop.add(prod);
      }
    }
    return sop;
  }

  /**
   * 'P' stands for 'Partial'
   */
  interface PResult {}
  static final PResult Identity = new PResult() {
    @Override
    public String toString() {
      return "Identity";
    }
  };
  // similar to top, maximal element
  static final PResult Return = new PResult() {
    @Override
    public String toString() {
      return "Return";
    }
  };
  // minimal element
  static final PResult NPE = new PResult() {
    @Override
    public String toString() {
      return "NPE";
    }
  };
  static final class ConditionalNPE implements PResult {
    final Set<Set<Key>> sop;
    public ConditionalNPE(Set<Set<Key>> sop) throws AnalyzerException {
      this.sop = sop;
      checkLimit(sop);
    }

    public ConditionalNPE(Key key) {
      sop = new HashSet<>();
      Set<Key> prod = new HashSet<>();
      prod.add(key);
      sop.add(prod);
    }

    static void checkLimit(Set<Set<Key>> sop) throws AnalyzerException {
      int size = 0;
      for (Set<Key> keys : sop) {
        size += keys.size();
      }
      if (size > Analysis.EQUATION_SIZE_LIMIT) {
        throw new AnalyzerException(null, "Equation size is too big");
      }
    }
  }

  static PResult combineNullable(PResult r1, PResult r2) throws AnalyzerException {
    if (Identity == r1) return r2;
    if (Identity == r2) return r1;
    if (Return == r1) return r2;
    if (Return == r2) return r1;
    if (NPE == r1) return NPE;
    if (NPE == r2) return NPE;
    ConditionalNPE cnpe1 = (ConditionalNPE) r1;
    ConditionalNPE cnpe2 = (ConditionalNPE) r2;
    return new ConditionalNPE(join(cnpe1.sop, cnpe2.sop));
  }

  static PResult join(PResult r1, PResult r2) throws AnalyzerException {
    if (Identity == r1) return r2;
    if (Identity == r2) return r1;
    if (Return == r1) return Return;
    if (Return == r2) return Return;
    if (NPE == r1) return r2;
    if (NPE == r2) return r1;
    ConditionalNPE cnpe1 = (ConditionalNPE) r1;
    ConditionalNPE cnpe2 = (ConditionalNPE) r2;
    return new ConditionalNPE(join(cnpe1.sop, cnpe2.sop));
  }

  static PResult meet(PResult r1, PResult r2) throws AnalyzerException {
    if (Identity == r1) return r2;
    if (Return == r1) return r2;
    if (Return == r2) return r1;
    if (NPE == r1) return NPE;
    if (NPE == r2) return NPE;
    if (Identity == r2) return Identity;
    ConditionalNPE cnpe1 = (ConditionalNPE) r1;
    ConditionalNPE cnpe2 = (ConditionalNPE) r2;
    return new ConditionalNPE(meet(cnpe1.sop, cnpe2.sop));
  }

}

interface PendingAction {}
class ProceedState implements PendingAction {
  final State state;

  ProceedState(State state) {
    this.state = state;
  }
}
class MakeResult implements PendingAction {
  final State state;
  final PResult subResult;
  final int[] indices;

  MakeResult(State state, PResult subResult, int[] indices) {
    this.state = state;
    this.subResult = subResult;
    this.indices = indices;
  }
}

class NonNullInAnalysis extends Analysis<PResult> {
  final private PendingAction[] pendingActions;
  private final PResult[] results;
  private final NotNullInterpreter interpreter = new NotNullInterpreter();

  // Flag saying that at some branch NPE was found. Used later as an evidence that this param is *NOT* @Nullable (optimization).
  boolean possibleNPE;

  protected NonNullInAnalysis(RichControlFlow richControlFlow,
                              Direction direction,
                              boolean stable,
                              PendingAction[] pendingActions,
                              PResult[] results) {
    super(richControlFlow, direction, stable);
    this.pendingActions = pendingActions;
    this.results = results;
  }

  PResult combineResults(PResult delta, int[] subResults) throws AnalyzerException {
    PResult result = Identity;
    for (int subResult : subResults) {
      result = join(result, results[subResult]);
    }
    return meet(delta, result);
  }

  @NotNull
  Equation mkEquation(PResult result) {
    if (Identity == result || Return == result) {
      return new Equation(aKey, new Final(Value.Top));
    }
    else if (NPE == result) {
      return new Equation(aKey, new Final(Value.NotNull));
    }
    else {
      ConditionalNPE condNpe = (ConditionalNPE) result;
      Set<Product> components = new HashSet<>();
      for (Set<Key> prod : condNpe.sop) {
        components.add(new Product(Value.Top, prod));
      }
      return new Equation(aKey, new Pending(components));
    }
  }

  private int id;
  private Frame<BasicValue> nextFrame;
  private PResult subResult;

  @NotNull
  protected Equation analyze() throws AnalyzerException {
    pendingPush(new ProceedState(createStartState()));
    int steps = 0;
    while (pendingTop > 0 && earlyResult == null) {
      steps ++;
      if (steps >= STEPS_LIMIT) {
        throw new AnalyzerException(null, "limit is reached, steps: " + steps + " in method " + method);
      }
      PendingAction action = pendingActions[--pendingTop];
      if (action instanceof MakeResult) {
        MakeResult makeResult = (MakeResult) action;
        PResult result = combineResults(makeResult.subResult, makeResult.indices);
        State state = makeResult.state;
        int insnIndex = state.conf.insnIndex;
        results[state.index] = result;
        addComputed(insnIndex, state);
      }
      else if (action instanceof ProceedState) {
        ProceedState proceedState = (ProceedState) action;
        State state = proceedState.state;
        int insnIndex = state.conf.insnIndex;
        Conf conf = state.conf;
        List<Conf> history = state.history;

        boolean fold = false;
        if (dfsTree.loopEnters[insnIndex]) {
          for (Conf prev : history) {
            if (AbstractValues.isInstance(conf, prev)) {
              fold = true;
              break;
            }
          }
        }
        if (fold) {
          results[state.index] = Identity;
          addComputed(insnIndex, state);
        }
        else {
          State baseState = null;
          List<State> thisComputed = computed[insnIndex];
          if (thisComputed != null) {
            for (State prevState : thisComputed) {
              if (stateEquiv(state, prevState)) {
                baseState = prevState;
                break;
              }
            }
          }
          if (baseState != null) {
            results[state.index] = results[baseState.index];
          } else {
            // the main call
            processState(state);
          }

        }
      }
    }
    if (earlyResult != null) {
      return mkEquation(earlyResult);
    } else {
      return mkEquation(results[0]);
    }
  }

  private void processState(State state) throws AnalyzerException {
    int stateIndex = state.index;
    Conf conf = state.conf;
    int insnIndex = conf.insnIndex;
    List<Conf> history = state.history;
    boolean taken = state.taken;
    Frame<BasicValue> frame = conf.frame;
    AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
    List<Conf> nextHistory = dfsTree.loopEnters[insnIndex] ? append(history, conf) : history;
    boolean hasCompanions = state.hasCompanions;
    execute(frame, insnNode);

    boolean notEmptySubResult = subResult != Identity;

    if (subResult == NPE) {
      results[stateIndex] = NPE;
      possibleNPE = true;
      addComputed(insnIndex, state);
      return;
    }

    int opcode = insnNode.getOpcode();
    switch (opcode) {
      case ARETURN:
      case IRETURN:
      case LRETURN:
      case FRETURN:
      case DRETURN:
      case RETURN:
        if (!hasCompanions) {
          earlyResult = Return;
        } else {
          results[stateIndex] = Return;
          addComputed(insnIndex, state);
        }
        return;
      default:
    }

    if (opcode == ATHROW) {
      if (taken) {
        results[stateIndex] = NPE;
        possibleNPE = true;
      } else {
        results[stateIndex] = Identity;
      }
      addComputed(insnIndex, state);
      return;
    }

    if (opcode == IFNONNULL && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = insnIndex + 1;
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, hasCompanions || notEmptySubResult);
      pendingPush(new MakeResult(state, subResult, new int[]{nextState.index}));
      pendingPush(new ProceedState(nextState));
      return;
    }

    if (opcode == IFNULL && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, hasCompanions || notEmptySubResult);
      pendingPush(new MakeResult(state, subResult, new int[]{nextState.index}));
      pendingPush(new ProceedState(nextState));
      return;
    }

    if (opcode == IFEQ && popValue(frame) == InstanceOfCheckValue) {
      int nextInsnIndex = methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, hasCompanions || notEmptySubResult);
      pendingPush(new MakeResult(state, subResult, new int[]{nextState.index}));
      pendingPush(new ProceedState(nextState));
      return;
    }

    if (opcode == IFNE && popValue(frame) == InstanceOfCheckValue) {
      int nextInsnIndex = insnIndex + 1;
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, hasCompanions || notEmptySubResult);
      pendingPush(new MakeResult(state, subResult, new int[]{nextState.index}));
      pendingPush(new ProceedState(nextState));
      return;
    }

    // general case
    int[] nextInsnIndices = controlFlow.transitions[insnIndex];
    int[] subIndices = new int[nextInsnIndices.length];
    for (int i = 0; i < nextInsnIndices.length; i++) {
      subIndices[i] = ++id;
    }
    pendingPush(new MakeResult(state, subResult, subIndices));
    for (int i = 0; i < nextInsnIndices.length; i++) {
      int nextInsnIndex = nextInsnIndices[i];
      Frame<BasicValue> nextFrame1 = nextFrame;
      if (controlFlow.errors[nextInsnIndex] && controlFlow.errorTransitions.contains(new Edge(insnIndex, nextInsnIndex))) {
        nextFrame1 = new Frame<>(frame);
        nextFrame1.clearStack();
        nextFrame1.push(ASMUtils.THROWABLE_VALUE);
      }
      pendingPush(new ProceedState(new State(subIndices[i], new Conf(nextInsnIndex, nextFrame1), nextHistory, taken, hasCompanions || notEmptySubResult)));
    }

  }

  private int pendingTop;

  private void pendingPush(PendingAction action) throws AnalyzerException {
    if (pendingTop >= STEPS_LIMIT) {
      throw new AnalyzerException(null, "limit is reached in method " + method);
    }
    pendingActions[pendingTop++] = action;
  }

  private void execute(Frame<BasicValue> frame, AbstractInsnNode insnNode) throws AnalyzerException {
    switch (insnNode.getType()) {
      case AbstractInsnNode.LABEL:
      case AbstractInsnNode.LINE:
      case AbstractInsnNode.FRAME:
        nextFrame = frame;
        subResult = Identity;
        break;
      default:
        nextFrame = new Frame<>(frame);
        interpreter.reset(false);
        nextFrame.execute(insnNode, interpreter);
        subResult = interpreter.getSubResult();
    }
  }
}

class NullableInAnalysis extends Analysis<PResult> {
  final private State[] pending;

  private final NullableInterpreter interpreter = new NullableInterpreter();

  protected NullableInAnalysis(RichControlFlow richControlFlow, Direction direction, boolean stable, State[] pending) {
    super(richControlFlow, direction, stable);
    this.pending = pending;
  }

  @NotNull
  Equation mkEquation(PResult result) {
    if (NPE == result) {
      return new Equation(aKey, new Final(Value.Top));
    }
    if (Identity == result || Return == result) {
      return new Equation(aKey, new Final(Value.Null));
    }
    else {
      ConditionalNPE condNpe = (ConditionalNPE) result;
      Set<Product> components = new HashSet<>();
      for (Set<Key> prod : condNpe.sop) {
        components.add(new Product(Value.Top, prod));
      }
      return new Equation(aKey, new Pending(components));
    }
  }

  private int id;
  private Frame<BasicValue> nextFrame;
  private PResult myResult = Identity;
  private PResult subResult = Identity;
  private boolean top;

  @NotNull
  protected Equation analyze() throws AnalyzerException {
    pendingPush(createStartState());
    int steps = 0;
    while (pendingTop > 0 && earlyResult == null) {
      steps ++;
      if (steps >= STEPS_LIMIT) {
        throw new AnalyzerException(null, "limit is reached, steps: " + steps + " in method " + method);
      }
      State state = pending[--pendingTop];
      int insnIndex = state.conf.insnIndex;
      Conf conf = state.conf;
      List<Conf> history = state.history;

      boolean fold = false;
      if (dfsTree.loopEnters[insnIndex]) {
        for (Conf prev : history) {
          if (AbstractValues.isInstance(conf, prev)) {
            fold = true;
            break;
          }
        }
      }
      if (fold) {
        addComputed(insnIndex, state);
      }
      else {
        State baseState = null;
        List<State> thisComputed = computed[insnIndex];
        if (thisComputed != null) {
          for (State prevState : thisComputed) {
            if (stateEquiv(state, prevState)) {
              baseState = prevState;
              break;
            }
          }
        }
        if (baseState == null) {
          processState(state);
        }
      }
    }
    if (earlyResult != null) {
      return mkEquation(earlyResult);
    } else {
      return mkEquation(myResult);
    }
  }

  private void processState(State state) throws AnalyzerException {
    Conf conf = state.conf;
    int insnIndex = conf.insnIndex;
    List<Conf> history = state.history;
    boolean taken = state.taken;
    Frame<BasicValue> frame = conf.frame;
    AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
    List<Conf> nextHistory = dfsTree.loopEnters[insnIndex] ? append(history, conf) : history;

    addComputed(insnIndex, state);
    execute(frame, insnNode, taken);

    if (subResult == NPE || top) {
      earlyResult = NPE;
      return;
    }

    if (subResult instanceof ConditionalNPE) {
      myResult = combineNullable(myResult, subResult);
    }

    int opcode = insnNode.getOpcode();
    switch (opcode) {
      case ARETURN:
        if (popValue(frame) instanceof ParamValue) {
          earlyResult = NPE;
        }
        return;
      case IRETURN:
      case LRETURN:
      case FRETURN:
      case DRETURN:
      case RETURN:
        return;
      default:
    }

    if (opcode == ATHROW) {
      if (taken) {
        earlyResult = NPE;
      }
      return;
    }

    if (opcode == IFNONNULL && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = insnIndex + 1;
      pendingPush(new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false));
      return;
    }

    if (opcode == IFNULL && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      pendingPush(new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false));
      return;
    }

    if (opcode == IFEQ && popValue(frame) == InstanceOfCheckValue) {
      int nextInsnIndex = methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      pendingPush(new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false));
      return;
    }

    if (opcode == IFNE && popValue(frame) == InstanceOfCheckValue) {
      int nextInsnIndex = insnIndex + 1;
      pendingPush(new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false));
      return;
    }

    // general case
    for (int nextInsnIndex : controlFlow.transitions[insnIndex]) {
      Frame<BasicValue> nextFrame1 = nextFrame;
      if (controlFlow.errors[nextInsnIndex] && controlFlow.errorTransitions.contains(new Edge(insnIndex, nextInsnIndex))) {
        nextFrame1 = new Frame<>(frame);
        nextFrame1.clearStack();
        nextFrame1.push(ASMUtils.THROWABLE_VALUE);
      }
      pendingPush(new State(++id, new Conf(nextInsnIndex, nextFrame1), nextHistory, taken, false));
    }

  }

  private int pendingTop;

  private void pendingPush(State state) throws AnalyzerException {
    if (pendingTop >= STEPS_LIMIT) {
      throw new AnalyzerException(null, "limit is reached in method " + method);
    }
    pending[pendingTop++] = state;
  }

  private void execute(Frame<BasicValue> frame, AbstractInsnNode insnNode, boolean taken) throws AnalyzerException {
    switch (insnNode.getType()) {
      case AbstractInsnNode.LABEL:
      case AbstractInsnNode.LINE:
      case AbstractInsnNode.FRAME:
        nextFrame = frame;
        subResult = Identity;
        top = false;
        break;
      default:
        nextFrame = new Frame<>(frame);
        interpreter.reset(taken);
        nextFrame.execute(insnNode, interpreter);
        subResult = interpreter.getSubResult();
        top = interpreter.top;
    }
  }
}

abstract class NullityInterpreter extends BasicInterpreter {
  boolean top;
  final boolean nullableAnalysis;
  final int nullityMask;
  private PResult subResult = Identity;
  protected boolean taken;

  NullityInterpreter(boolean nullableAnalysis, int nullityMask) {
    this.nullableAnalysis = nullableAnalysis;
    this.nullityMask = nullityMask;
  }

  abstract PResult combine(PResult res1, PResult res2) throws AnalyzerException;

  public PResult getSubResult() {
    return subResult;
  }
  void reset(boolean taken) {
    subResult = Identity;
    top = false;
    this.taken = taken;
  }

  @Override
  public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
    switch (insn.getOpcode()) {
      case GETFIELD:
      case ARRAYLENGTH:
      case MONITORENTER:
        if (value instanceof ParamValue) {
          subResult = NPE;
        }
        break;
      case CHECKCAST:
        if (value instanceof ParamValue) {
          return new ParamValue(Type.getObjectType(((TypeInsnNode)insn).desc));
        }
        break;
      case INSTANCEOF:
        if (value instanceof ParamValue) {
          return InstanceOfCheckValue;
        }
        break;
      default:

    }
    return super.unaryOperation(insn, value);
  }

  @Override
  public BasicValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2) throws AnalyzerException {
    switch (insn.getOpcode()) {
      case IALOAD:
      case LALOAD:
      case FALOAD:
      case DALOAD:
      case AALOAD:
      case BALOAD:
      case CALOAD:
      case SALOAD:
        if (value1 instanceof ParamValue) {
          subResult = NPE;
        }
        break;
      case PUTFIELD:
        if (value1 instanceof ParamValue) {
          subResult = NPE;
        }
        if (nullableAnalysis && value2 instanceof ParamValue) {
          subResult = NPE;
        }
        break;
      default:
    }
    return super.binaryOperation(insn, value1, value2);
  }

  @Override
  public BasicValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2, BasicValue value3) throws AnalyzerException {
    switch (insn.getOpcode()) {
      case IASTORE:
      case LASTORE:
      case FASTORE:
      case DASTORE:
      case BASTORE:
      case CASTORE:
      case SASTORE:
        if (value1 instanceof ParamValue) {
          subResult = NPE;
        }
        break;
      case AASTORE:
        if (value1 instanceof ParamValue) {
          subResult = NPE;
        }
        if (nullableAnalysis && value3 instanceof ParamValue) {
          subResult = NPE;
        }
        break;
      default:
    }
    return null;
  }

  @Override
  public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
    int opcode = insn.getOpcode();
    boolean isStaticInvoke = opcode == INVOKESTATIC;
    int shift = isStaticInvoke ? 0 : 1;
    if ((opcode == INVOKESPECIAL || opcode ==INVOKEINTERFACE || opcode == INVOKEVIRTUAL) && values.get(0) instanceof ParamValue) {
      subResult = NPE;
    }
    switch (opcode) {
      case INVOKEINTERFACE:
        if (nullableAnalysis) {
          for (int i = shift; i < values.size(); i++) {
            if (values.get(i) instanceof ParamValue) {
                top = true;
                return super.naryOperation(insn, values);
            }
          }
        }
        break;
      case INVOKESTATIC:
      case INVOKESPECIAL:
      case INVOKEVIRTUAL:
        boolean stable = opcode == INVOKESTATIC || opcode == INVOKESPECIAL;
        MethodInsnNode methodNode = (MethodInsnNode) insn;
        Method method = new Method(methodNode.owner, methodNode.name, methodNode.desc);
        for (int i = shift; i < values.size(); i++) {
          BasicValue value = values.get(i);
          if (value instanceof ParamValue || (NullValue == value && nullityMask == In.NULLABLE_MASK && "<init>".equals(methodNode.name))) {
            subResult = combine(subResult, new ConditionalNPE(new Key(method, new In(i - shift, nullityMask), stable)));
          }
        }
        break;
      default:
    }
    return super.naryOperation(insn, values);
  }
}

class NotNullInterpreter extends NullityInterpreter {

  NotNullInterpreter() {
    super(false, In.NOT_NULL_MASK);
  }

  @Override
  PResult combine(PResult res1, PResult res2) throws AnalyzerException {
    return meet(res1, res2);
  }
}

class NullableInterpreter extends NullityInterpreter {

  NullableInterpreter() {
    super(true, In.NULLABLE_MASK);
  }

  @Override
  public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
    if (insn.getOpcode() == ACONST_NULL && taken) {
      return NullValue;
    }
    return super.newOperation(insn);
  }

  @Override
  PResult combine(PResult res1, PResult res2) throws AnalyzerException {
    return join(res1, res2);
  }
}
