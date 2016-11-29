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
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.Handle;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.*;
import static com.intellij.codeInspection.bytecodeAnalysis.Direction.InOut;
import static com.intellij.codeInspection.bytecodeAnalysis.Direction.Out;
import static org.jetbrains.org.objectweb.asm.Opcodes.*;

class InOutAnalysis extends Analysis<Result> {

  static final ResultUtil resultUtil =
    new ResultUtil(new ELattice<>(Value.Bot, Value.Top));

  final private State[] pending;
  private final InOutInterpreter interpreter;
  private final Value inValue;
  private final int generalizeShift;
  private Result internalResult;
  private int id;
  private int pendingTop;

  protected InOutAnalysis(RichControlFlow richControlFlow, Direction direction, boolean[] resultOrigins, boolean stable, State[] pending) {
    super(richControlFlow, direction, stable);
    this.pending = pending;
    interpreter = new InOutInterpreter(direction, richControlFlow.controlFlow.methodNode.instructions, resultOrigins);
    inValue = direction instanceof InOut ? ((InOut)direction).inValue : null;
    generalizeShift = (methodNode.access & ACC_STATIC) == 0 ? 1 : 0;
    internalResult = new Final(Value.Bot);
  }

  @NotNull
  Equation mkEquation(Result res) {
    return new Equation(aKey, res);
  }

  @NotNull
  protected Equation analyze() throws AnalyzerException {
    pendingPush(createStartState());
    int steps = 0;
    while (pendingTop > 0 && earlyResult == null) {
      steps ++;
      if (steps >= STEPS_LIMIT) {
        throw new AnalyzerException(null, "limit is reached, steps: " + steps + " in method " + method);
      }
      if (steps % 128 == 0) {
        ProgressManager.checkCanceled();
      }
      State state = pending[--pendingTop];
      int insnIndex = state.conf.insnIndex;
      Conf conf = state.conf;
      List<Conf> history = state.history;

      boolean fold = false;
      if (dfsTree.loopEnters[insnIndex]) {
        for (Conf prev : history) {
          if (isInstance(conf, prev)) {
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
      return mkEquation(internalResult);
    }
  }

  void processState(State state) throws AnalyzerException {
    Conf preConf = state.conf;
    int insnIndex = preConf.insnIndex;
    boolean loopEnter = dfsTree.loopEnters[insnIndex];
    Conf conf = loopEnter ? generalize(preConf) : preConf;
    List<Conf> history = state.history;
    boolean taken = state.taken;
    Frame<BasicValue> frame = conf.frame;
    AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
    List<Conf> nextHistory = loopEnter ? append(history, conf) : history;
    Frame<BasicValue> nextFrame = execute(frame, insnNode);

    addComputed(insnIndex, state);

    if (interpreter.deReferenced) {
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
        BasicValue stackTop = popValue(frame);
        Result subResult;
        if (FalseValue == stackTop) {
          subResult = new Final(Value.False);
        }
        else if (TrueValue == stackTop) {
          subResult = new Final(Value.True);
        }
        else if (NullValue == stackTop) {
          subResult = new Final(Value.Null);
        }
        else if (stackTop instanceof NotNullValue) {
          subResult = new Final(Value.NotNull);
        }
        else if (stackTop instanceof ParamValue) {
          subResult = new Final(inValue);
        }
        else if (stackTop instanceof CallResultValue) {
          Set<Key> keys = ((CallResultValue) stackTop).inters;
          subResult = new Pending(Collections.singleton(new Product(Value.Top, keys)));
        }
        else {
          earlyResult = new Final(Value.Top);
          return;
        }
        internalResult = resultUtil.join(internalResult, subResult);
        if (internalResult instanceof Final && ((Final)internalResult).value == Value.Top) {
          earlyResult = internalResult;
        }
        return;
      case ATHROW:
        return;
      default:
    }

    if (opcode == IFNONNULL && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = inValue == Value.Null ? insnIndex + 1 : methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false);
      pendingPush(nextState);
      return;
    }

    if (opcode == IFNULL && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = inValue == Value.NotNull ? insnIndex + 1 : methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false);
      pendingPush(nextState);
      return;
    }

    if (opcode == IFEQ && popValue(frame) == InstanceOfCheckValue && inValue == Value.Null) {
      int nextInsnIndex = methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false);
      pendingPush(nextState);
      return;
    }

    if (opcode == IFNE && popValue(frame) == InstanceOfCheckValue && inValue == Value.Null) {
      int nextInsnIndex = insnIndex + 1;
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false);
      pendingPush(nextState);
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

  private void pendingPush(State st) throws AnalyzerException {
    if (pendingTop >= STEPS_LIMIT) {
      throw new AnalyzerException(null, "limit is reached in method " + method);
    }
    pending[pendingTop++] = st;
  }

  private Frame<BasicValue> execute(Frame<BasicValue> frame, AbstractInsnNode insnNode) throws AnalyzerException {
    interpreter.deReferenced = false;
    switch (insnNode.getType()) {
      case AbstractInsnNode.LABEL:
      case AbstractInsnNode.LINE:
      case AbstractInsnNode.FRAME:
        return frame;
      default:
        Frame<BasicValue> nextFrame = new Frame<>(frame);
        nextFrame.execute(insnNode, interpreter);
        return nextFrame;
    }
  }

  private Conf generalize(Conf conf) {
    Frame<BasicValue> frame = new Frame<>(conf.frame);
    for (int i = generalizeShift; i < frame.getLocals(); i++) {
      BasicValue value = frame.getLocal(i);
      Class<?> valueClass = value.getClass();
      if (valueClass != BasicValue.class && valueClass != ParamValue.class) {
        frame.setLocal(i, new BasicValue(value.getType()));
      }
    }

    BasicValue[] stack = new BasicValue[frame.getStackSize()];
    for (int i = 0; i < frame.getStackSize(); i++) {
      stack[i] = frame.getStack(i);
    }
    frame.clearStack();

    for (BasicValue value : stack) {
      Class<?> valueClass = value.getClass();
      if (valueClass != BasicValue.class && valueClass != ParamValue.class) {
        frame.push(new BasicValue(value.getType()));
      } else {
        frame.push(value);
      }
    }

    return new Conf(conf.insnIndex, frame);
  }
}

class InOutInterpreter extends BasicInterpreter {
  final Direction direction;
  final InsnList insns;
  final boolean[] resultOrigins;
  final boolean nullAnalysis;

  boolean deReferenced;

  InOutInterpreter(Direction direction, InsnList insns, boolean[] resultOrigins) {
    this.direction = direction;
    this.insns = insns;
    this.resultOrigins = resultOrigins;
    nullAnalysis = (direction instanceof InOut) && (((InOut)direction).inValue) == Value.Null;
  }

  @Override
  public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
    boolean propagate = resultOrigins[insns.indexOf(insn)];
    if (propagate) {
      switch (insn.getOpcode()) {
        case ICONST_0:
          return FalseValue;
        case ICONST_1:
          return TrueValue;
        case ACONST_NULL:
          return NullValue;
        case LDC:
          Object cst = ((LdcInsnNode)insn).cst;
          if (cst instanceof Type) {
            Type type = (Type)cst;
            if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
              return CLASS_VALUE;
            }
            if (type.getSort() == Type.METHOD) {
              return METHOD_VALUE;
            }
          }
          else if (cst instanceof String) {
            return STRING_VALUE;
          }
          else if (cst instanceof Handle) {
            return METHOD_HANDLE_VALUE;
          }
          break;
        case NEW:
          return new NotNullValue(Type.getObjectType(((TypeInsnNode)insn).desc));
        default:
      }
    }
    return super.newOperation(insn);
  }

  @Override
  public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
    boolean propagate = resultOrigins[insns.indexOf(insn)];
    switch (insn.getOpcode()) {
      case GETFIELD:
      case ARRAYLENGTH:
      case MONITORENTER:
        if (nullAnalysis && value instanceof ParamValue) {
          deReferenced = true;
        }
        return super.unaryOperation(insn, value);
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
      case NEWARRAY:
      case ANEWARRAY:
        if (propagate) {
          return new NotNullValue(super.unaryOperation(insn, value).getType());
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
        if (nullAnalysis && value1 instanceof ParamValue) {
          deReferenced = true;
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
        if (nullAnalysis && value1 instanceof ParamValue) {
          deReferenced = true;
        }
      default:
    }
    return null;
  }

  @Override
  public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
    boolean propagate = resultOrigins[insns.indexOf(insn)];
    int opCode = insn.getOpcode();
    int shift = opCode == INVOKESTATIC ? 0 : 1;

    switch (opCode) {
      case INVOKESPECIAL:
      case INVOKEINTERFACE:
      case INVOKEVIRTUAL:
        if (nullAnalysis && values.get(0) instanceof ParamValue) {
          deReferenced = true;
          return super.naryOperation(insn, values);
        }
    }

    if (propagate) {
      switch (opCode) {
        case INVOKESTATIC:
        case INVOKESPECIAL:
        case INVOKEVIRTUAL:
        case INVOKEINTERFACE:
          boolean stable = opCode == INVOKESTATIC || opCode == INVOKESPECIAL;
          MethodInsnNode mNode = (MethodInsnNode)insn;
          Method method = new Method(mNode.owner, mNode.name, mNode.desc);
          Type retType = Type.getReturnType(mNode.desc);
          boolean isRefRetType = retType.getSort() == Type.OBJECT || retType.getSort() == Type.ARRAY;
          if (!Type.VOID_TYPE.equals(retType)) {
            if (direction instanceof InOut) {
              InOut inOut = (InOut)direction;
              HashSet<Key> keys = new HashSet<>();
              for (int i = shift; i < values.size(); i++) {
                if (values.get(i) instanceof ParamValue) {
                  keys.add(new Key(method, new InOut(i - shift, inOut.inValue), stable));
                }
              }
              if (isRefRetType) {
                keys.add(new Key(method, Out, stable));
              }
              if (!keys.isEmpty()) {
                return new CallResultValue(retType, keys);
              }
            }
            else if (isRefRetType) {
              HashSet<Key> keys = new HashSet<>();
              keys.add(new Key(method, Out, stable));
              return new CallResultValue(retType, keys);
            }
          }
          break;
        case MULTIANEWARRAY:
          return new NotNullValue(super.naryOperation(insn, values).getType());
        default:
      }
    }
    return super.naryOperation(insn, values);
  }
}
