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

import com.intellij.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph.Edge;
import com.intellij.codeInspection.bytecodeAnalysis.asm.RichControlFlow;
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
import static org.jetbrains.org.objectweb.asm.Opcodes.*;

class InOutAnalysis extends Analysis<Result<Key, Value>> {
  private static final ThreadLocal<Result<Key, Value>[]> ourResults = new ThreadLocal<Result<Key, Value>[]>() {
    @Override
    protected Result<Key, Value>[] initialValue() {
      return new Result[Analysis.STEPS_LIMIT];
    }
  };

  static final ResultUtil<Key, Value> resultUtil =
    new ResultUtil<Key, Value>(new ELattice<Value>(Value.Bot, Value.Top));

  private final InOutInterpreter interpreter;
  private final Value inValue;
  private final int generalizeShift;

  protected InOutAnalysis(RichControlFlow richControlFlow, Direction direction, boolean[] resultOrigins, boolean stable) {
    super(richControlFlow, direction, stable, ourResults.get());
    interpreter = new InOutInterpreter(direction, richControlFlow.controlFlow.methodNode.instructions, resultOrigins);
    inValue = direction instanceof InOut ? ((InOut)direction).inValue : null;
    generalizeShift = (methodNode.access & ACC_STATIC) == 0 ? 1 : 0;
  }

  @Override
  Result<Key, Value> identity() {
    return new Final<Key, Value>(Value.Bot);
  }

  @Override
  Result<Key, Value> combineResults(Result<Key, Value> delta, int[] subResults) throws AnalyzerException {
    Result<Key, Value> result = null;
    for (int subResult : subResults) {
      if (result == null) {
        result = results[subResult];
      } else {
        result = resultUtil.join(result, results[subResult]);
      }
    }
    return result;
  }

  @Override
  boolean isEarlyResult(Result<Key, Value> res) {
    if (res instanceof Final) {
      return ((Final<?, Value>)res).value == Value.Top;
    }
    return false;
  }

  @NotNull
  @Override
  Equation<Key, Value> mkEquation(Result<Key, Value> res) {
    return new Equation<Key, Value>(aKey, res);
  }

  private int id = 0;

  @Override
  void processState(State state) throws AnalyzerException {
    int stateIndex = state.index;
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

    if (interpreter.deReferenced) {
      results[stateIndex] = new Final<Key, Value>(Value.Bot);
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
        BasicValue stackTop = popValue(frame);
        if (FalseValue == stackTop) {
          results[stateIndex] = new Final<Key, Value>(Value.False);
          addComputed(insnIndex, state);
        }
        else if (TrueValue == stackTop) {
          results[stateIndex] = new Final<Key, Value>(Value.True);
          addComputed(insnIndex, state);
        }
        else if (NullValue == stackTop) {
          results[stateIndex] = new Final<Key, Value>(Value.Null);
          addComputed(insnIndex, state);
        }
        else if (stackTop instanceof NotNullValue) {
          results[stateIndex] = new Final<Key, Value>(Value.NotNull);
          addComputed(insnIndex, state);
        }
        else if (stackTop instanceof ParamValue) {
          results[stateIndex] = new Final<Key, Value>(inValue);
          addComputed(insnIndex, state);
        }
        else if (stackTop instanceof CallResultValue) {
          Set<Key> keys = ((CallResultValue) stackTop).inters;
          results[stateIndex] = new Pending<Key, Value>(Collections.singleton(new Product<Key, Value>(Value.Top, keys)));
          addComputed(insnIndex, state);
        }
        else {
          earlyResult = new Final<Key, Value>(Value.Top);
        }
        return;
      case ATHROW:
        results[stateIndex] = new Final<Key, Value>(Value.Bot);
        addComputed(insnIndex, state);
        return;
      default:
    }

    if (opcode == IFNONNULL && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = inValue == Value.Null ? insnIndex + 1 : methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false);
      pendingPush(new MakeResult<Result<Key, Value>>(state, myIdentity, new int[]{nextState.index}));
      pendingPush(new ProceedState<Result<Key, Value>>(nextState));
      return;
    }

    if (opcode == IFNULL && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = inValue == Value.NotNull ? insnIndex + 1 : methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false);
      pendingPush(new MakeResult<Result<Key, Value>>(state, myIdentity, new int[]{nextState.index}));
      pendingPush(new ProceedState<Result<Key, Value>>(nextState));
      return;
    }

    if (opcode == IFEQ && popValue(frame) == InstanceOfCheckValue && inValue == Value.Null) {
      int nextInsnIndex = methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false);
      pendingPush(new MakeResult<Result<Key, Value>>(state, myIdentity, new int[]{nextState.index}));
      pendingPush(new ProceedState<Result<Key, Value>>(nextState));
      return;
    }

    if (opcode == IFNE && popValue(frame) == InstanceOfCheckValue && inValue == Value.Null) {
      int nextInsnIndex = insnIndex + 1;
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false);
      pendingPush(new MakeResult<Result<Key, Value>>(state, myIdentity, new int[]{nextState.index}));
      pendingPush(new ProceedState<Result<Key, Value>>(nextState));
      return;
    }

    if (opcode == IFEQ && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = inValue == Value.True ? insnIndex + 1 : methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false);
      pendingPush(new MakeResult<Result<Key, Value>>(state, myIdentity, new int[]{nextState.index}));
      pendingPush(new ProceedState<Result<Key, Value>>(nextState));
      return;
    }

    if (opcode == IFNE && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = inValue == Value.False ? insnIndex + 1 : methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false);
      pendingPush(new MakeResult<Result<Key, Value>>(state, myIdentity, new int[]{nextState.index}));
      pendingPush(new ProceedState<Result<Key, Value>>(nextState));
      return;
    }

    // general case
    int[] nextInsnIndices = controlFlow.transitions[insnIndex];
    int[] subIndices = new int[nextInsnIndices.length];
    for (int i = 0; i < nextInsnIndices.length; i++) {
      subIndices[i] = ++id;
    }
    pendingPush(new MakeResult<Result<Key, Value>>(state, myIdentity, subIndices));
    for (int i = 0; i < nextInsnIndices.length; i++) {
      int nextInsnIndex = nextInsnIndices[i];
      Frame<BasicValue> nextFrame1 = nextFrame;
      if (controlFlow.errors[nextInsnIndex] && controlFlow.errorTransitions.contains(new Edge(insnIndex, nextInsnIndex))) {
        nextFrame1 = new Frame<BasicValue>(frame);
        nextFrame1.clearStack();
        nextFrame1.push(ASMUtils.THROWABLE_VALUE);
      }
      pendingPush(new ProceedState<Result<Key, Value>>(
        new State(subIndices[i], new Conf(nextInsnIndex, nextFrame1), nextHistory, taken, false)));
    }
  }

  private Frame<BasicValue> execute(Frame<BasicValue> frame, AbstractInsnNode insnNode) throws AnalyzerException {
    interpreter.deReferenced = false;
    switch (insnNode.getType()) {
      case AbstractInsnNode.LABEL:
      case AbstractInsnNode.LINE:
      case AbstractInsnNode.FRAME:
        return frame;
      default:
        Frame<BasicValue> nextFrame = new Frame<BasicValue>(frame);
        nextFrame.execute(insnNode, interpreter);
        return nextFrame;
    }
  }

  private Conf generalize(Conf conf) {
    Frame<BasicValue> frame = new Frame<BasicValue>(conf.frame);
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

  boolean deReferenced = false;

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
    return super.ternaryOperation(insn, value1, value2, value3);
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
              HashSet<Key> keys = new HashSet<Key>();
              for (int i = shift; i < values.size(); i++) {
                if (values.get(i) instanceof ParamValue) {
                  keys.add(new Key(method, new InOut(i - shift, inOut.inValue), stable));
                }
              }
              if (isRefRetType) {
                keys.add(new Key(method, new Out(), stable));
              }
              if (!keys.isEmpty()) {
                return new CallResultValue(retType, keys);
              }
            }
            else if (isRefRetType) {
              HashSet<Key> keys = new HashSet<Key>();
              keys.add(new Key(method, new Out(), stable));
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
