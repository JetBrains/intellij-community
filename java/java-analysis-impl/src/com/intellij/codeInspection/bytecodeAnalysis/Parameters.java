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

import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.JumpInsnNode;
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode;
import org.jetbrains.org.objectweb.asm.tree.TypeInsnNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame;

import java.util.*;

import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.InstanceOfCheckValue;
import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.ParamValue;
import static com.intellij.codeInspection.bytecodeAnalysis.PResults.*;
import static org.jetbrains.org.objectweb.asm.Opcodes.*;

abstract class PResults {
  // SoP = sum of products
  static Set<Set<Key>> join(Set<Set<Key>> sop1, Set<Set<Key>> sop2) {
    Set<Set<Key>> sop = new HashSet<Set<Key>>();
    sop.addAll(sop1);
    sop.addAll(sop2);
    return sop;
  }

  static Set<Set<Key>> meet(Set<Set<Key>> sop1, Set<Set<Key>> sop2) {
    Set<Set<Key>> sop = new HashSet<Set<Key>>();
    for (Set<Key> prod1 : sop1) {
      for (Set<Key> prod2 : sop2) {
        Set<Key> prod = new HashSet<Key>();
        prod.addAll(prod1);
        prod.addAll(prod2);
        sop.add(prod);
      }
    }
    return sop;
  }

  // Results
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
      sop = new HashSet<Set<Key>>();
      Set<Key> prod = new HashSet<Key>();
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

class NonNullInAnalysis extends Analysis<PResult> {

  private final NonNullInInterpreter interpreter = new NonNullInInterpreter();

  protected NonNullInAnalysis(RichControlFlow richControlFlow, Direction direction, boolean stable) {
    super(richControlFlow, direction, stable);
  }

  @Override
  PResult identity() {
    return Identity;
  }

  @Override
  PResult combineResults(PResult delta, List<PResult> subResults) throws AnalyzerException {
    PResult subResult = Identity;
    for (PResult sr : subResults) {
      subResult = join(subResult, sr);
    }
    return meet(delta, subResult);
  }

  @Override
  boolean isEarlyResult(PResult result) {
    return false;
  }

  @Override
  Equation<Key, Value> mkEquation(PResult result) {
    if (Identity == result || Return == result) {
      return new Equation<Key, Value>(aKey, new Final<Key, Value>(Value.Top));
    }
    else if (NPE == result) {
      return new Equation<Key, Value>(aKey, new Final<Key, Value>(Value.NotNull));
    }
    else {
      ConditionalNPE condNpe = (ConditionalNPE) result;
      Set<Product<Key, Value>> components = new HashSet<Product<Key, Value>>();
      for (Set<Key> prod : condNpe.sop) {
        components.add(new Product<Key, Value>(Value.Top, prod));
      }
      return new Equation<Key, Value>(aKey, new Pending<Key, Value>(components));
    }
  }

  private int id = 0;
  private Frame<BasicValue> nextFrame = null;
  private PResult subResult = null;

  @Override
  void processState(State state) throws AnalyzerException {
    int stateIndex = state.index;
    Conf conf = state.conf;
    int insnIndex = conf.insnIndex;
    List<Conf> history = state.history;
    boolean taken = state.taken;
    Frame<BasicValue> frame = conf.frame;
    AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
    List<Conf> nextHistory = dfsTree.loopEnters.contains(insnIndex) ? append(history, conf) : history;
    boolean hasCompanions = state.hasCompanions;
    execute(frame, insnNode);

    boolean notEmptySubResult = subResult != Identity;

    if (subResult == NPE) {
      results.put(stateIndex, NPE);
      computed.put(insnIndex, append(computed.get(insnIndex), state));
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
          results.put(stateIndex, Return);
          computed.put(insnIndex, append(computed.get(insnIndex), state));
        }
        return;
      default:
    }

    if (opcode == ATHROW) {
      if (taken) {
        results.put(stateIndex, NPE);
      } else {
        results.put(stateIndex, Identity);
      }
      computed.put(insnIndex, append(computed.get(insnIndex), state));
      return;
    }

    if (opcode == IFNONNULL && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = insnIndex + 1;
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, hasCompanions || notEmptySubResult);
      pending.push(new MakeResult<PResult>(state, subResult, new int[]{nextState.index}));
      pending.push(new ProceedState<PResult>(nextState));
      return;
    }

    if (opcode == IFNULL && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, hasCompanions || notEmptySubResult);
      pending.push(new MakeResult<PResult>(state, subResult, new int[]{nextState.index}));
      pending.push(new ProceedState<PResult>(nextState));
      return;
    }

    if (opcode == IFEQ && popValue(frame) == InstanceOfCheckValue) {
      int nextInsnIndex = methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, hasCompanions || notEmptySubResult);
      pending.push(new MakeResult<PResult>(state, subResult, new int[]{nextState.index}));
      pending.push(new ProceedState<PResult>(nextState));
      return;
    }

    if (opcode == IFNE && popValue(frame) == InstanceOfCheckValue) {
      int nextInsnIndex = insnIndex + 1;
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, hasCompanions || notEmptySubResult);
      pending.push(new MakeResult<PResult>(state, subResult, new int[]{nextState.index}));
      pending.push(new ProceedState<PResult>(nextState));
      return;
    }

    // general case
    int[] nextInsnIndices = controlFlow.transitions[insnIndex];
    List<State> nextStates = new ArrayList<State>(nextInsnIndices.length);
    int[] subIndices = new int[nextInsnIndices.length];

    for (int i = 0; i < nextInsnIndices.length; i++) {
      int nextInsnIndex = nextInsnIndices[i];
      Frame<BasicValue> nextFrame1 = nextFrame;
      if (controlFlow.errorTransitions.contains(new Edge(insnIndex, nextInsnIndex))) {
        nextFrame1 = new Frame<BasicValue>(frame);
        nextFrame1.clearStack();
        nextFrame1.push(new BasicValue(Type.getType("java/lang/Throwable")));
      }
      nextStates.add(new State(++id, new Conf(nextInsnIndex, nextFrame1), nextHistory, taken, hasCompanions || notEmptySubResult));
      subIndices[i] = (id);
    }

    pending.push(new MakeResult<PResult>(state, subResult, subIndices));
    for (State nextState : nextStates) {
      pending.push(new ProceedState<PResult>(nextState));
    }

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
        nextFrame = new Frame<BasicValue>(frame);
        interpreter.reset();
        nextFrame.execute(insnNode, interpreter);
        subResult = interpreter.getSubResult();
    }
  }
}

class NonNullInInterpreter extends BasicInterpreter {
  private PResult subResult = Identity;
  public PResult getSubResult() {
    return subResult;
  }
  void reset() {
    subResult = Identity;
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
      case PUTFIELD:
        if (value1 instanceof ParamValue) {
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
      case AASTORE:
      case BASTORE:
      case CASTORE:
      case SASTORE:
        if (value1 instanceof ParamValue) {
          subResult = NPE;
        }
      default:
    }
    return super.ternaryOperation(insn, value1, value2, value3);
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
      case INVOKESTATIC:
      case INVOKESPECIAL:
      case INVOKEVIRTUAL:
      case INVOKEINTERFACE:
        boolean stable = opcode == INVOKESTATIC || opcode == INVOKESPECIAL;
        MethodInsnNode methodNode = (MethodInsnNode) insn;
        for (int i = shift; i < values.size(); i++) {
          if (values.get(i) instanceof ParamValue) {
            Method method = new Method(methodNode.owner, methodNode.name, methodNode.desc);
            subResult = meet(subResult, new ConditionalNPE(new Key(method, new In(i - shift), stable)));
          }
        }
      default:
    }
    return super.naryOperation(insn, values);
  }
}
