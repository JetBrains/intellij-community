// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.Direction.ParamValueBasedDirection;
import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph.Edge;
import com.intellij.codeInspection.bytecodeAnalysis.asm.RichControlFlow;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.Handle;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame;

import java.util.*;

import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.*;
import static com.intellij.codeInspection.bytecodeAnalysis.Direction.Out;
import static com.intellij.codeInspection.bytecodeAnalysis.Direction.Throw;
import static org.jetbrains.org.objectweb.asm.Opcodes.*;

abstract class ContractAnalysis extends Analysis<Result> {
  static final ResultUtil resultUtil = new ResultUtil(new ELattice<>(Value.Bot, Value.Top));

  final private ExpandableArray<State> pending;
  final InOutInterpreter interpreter;
  final Value inValue;
  private final int generalizeShift;
  Result internalResult;
  boolean unsureOnly = true;
  private int id;
  private int pendingTop;

  protected ContractAnalysis(RichControlFlow richControlFlow, Direction direction, boolean[] resultOrigins, boolean stable, ExpandableArray<State> pending) {
    super(richControlFlow, direction, stable);
    this.pending = pending;
    interpreter = new InOutInterpreter(direction, richControlFlow.controlFlow.methodNode.instructions, resultOrigins);
    inValue = direction instanceof ParamValueBasedDirection ? ((ParamValueBasedDirection)direction).inValue : null;
    generalizeShift = (methodNode.access & ACC_STATIC) == 0 ? 1 : 0;
    internalResult = Value.Bot;
  }

  @NotNull
  Equation mkEquation(Result res) {
    return new Equation(aKey, res);
  }

  static Result checkLimit(Result result) throws AnalyzerException {
    if (result instanceof Pending) {
      int size = Arrays.stream(((Pending)result).delta).mapToInt(prod -> prod.ids.length).sum();
      if (size > Analysis.EQUATION_SIZE_LIMIT) {
        throw new AnalyzerException(null, "Equation size is too big");
      }
    }
    return result;
  }

  @Override
  @NotNull
  protected Equation analyze() throws AnalyzerException {
    pendingPush(createStartState());
    int steps = 0;
    while (pendingTop > 0 && earlyResult == null) {
      steps++;
      TooComplexException.check(method, steps);
      if (steps % 128 == 0) {
        ProgressManager.checkCanceled();
      }
      State state = Objects.requireNonNull(pending.get(--pendingTop));
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
            if (state.equiv(prevState)) {
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
    } else if (unsureOnly) {
      // We are not sure whether exceptional paths were actually taken or not
      // probably they handle exceptions which can never be thrown before dereference occurs
      return mkEquation(Value.Bot);
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

    int opcode = insnNode.getOpcode();

    if (interpreter.deReferenced && controlFlow.npeTransitions.containsKey(insnIndex)) {
      interpreter.deReferenced = false;
      int npeTarget = controlFlow.npeTransitions.get(insnIndex);
      for (int nextInsnIndex : controlFlow.transitions[insnIndex]) {
        if (!controlFlow.errorTransitions.contains(new Edge(insnIndex, nextInsnIndex))) continue;
        Frame<BasicValue> nextFrame1 = createCatchFrame(frame);
        boolean unsure = state.unsure || nextInsnIndex != npeTarget;
        pendingPush(new State(++id, new Conf(nextInsnIndex, nextFrame1), nextHistory, taken, false, unsure));
      }
      return;
    }

    if (handleReturn(frame, opcode, state.unsure)) return;

    if (opcode == IFNONNULL && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = inValue == Value.Null ? insnIndex + 1 : methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false, state.unsure);
      pendingPush(nextState);
      return;
    }

    if (opcode == IFNULL && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = inValue == Value.NotNull ? insnIndex + 1 : methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false, state.unsure);
      pendingPush(nextState);
      return;
    }

    if (opcode == IFEQ && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = inValue == Value.True ? insnIndex + 1 : methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false, state.unsure);
      pendingPush(nextState);
      return;
    }

    if (opcode == IFNE && popValue(frame) instanceof ParamValue) {
      int nextInsnIndex = inValue == Value.False ? insnIndex + 1 : methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false, state.unsure);
      pendingPush(nextState);
      return;
    }

    if (opcode == IFEQ && popValue(frame) == InstanceOfCheckValue && inValue == Value.Null) {
      int nextInsnIndex = methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false, state.unsure);
      pendingPush(nextState);
      return;
    }

    if (opcode == IFNE && popValue(frame) == InstanceOfCheckValue && inValue == Value.Null) {
      int nextInsnIndex = insnIndex + 1;
      State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false, state.unsure);
      pendingPush(nextState);
      return;
    }

    // general case
    for (int nextInsnIndex : controlFlow.transitions[insnIndex]) {
      Frame<BasicValue> nextFrame1 = nextFrame;
      boolean unsure = state.unsure;
      if (controlFlow.errors[nextInsnIndex] && controlFlow.errorTransitions.contains(new Edge(insnIndex, nextInsnIndex))) {
        nextFrame1 = createCatchFrame(frame);
        unsure = true;
      }
      pendingPush(new State(++id, new Conf(nextInsnIndex, nextFrame1), nextHistory, taken, false, unsure));
    }
  }

  abstract boolean handleReturn(Frame<BasicValue> frame, int opcode, boolean unsure) throws AnalyzerException;

  void pendingPush(State st) {
    TooComplexException.check(method, pendingTop);
    pending.set(pendingTop++, st);
  }

  private Frame<BasicValue> execute(Frame<BasicValue> frame, AbstractInsnNode insnNode) throws AnalyzerException {
    interpreter.deReferenced = false;
    return switch (insnNode.getType()) {
      case AbstractInsnNode.LABEL, AbstractInsnNode.LINE, AbstractInsnNode.FRAME -> frame;
      default -> {
        Frame<BasicValue> nextFrame = new Frame<>(frame);
        nextFrame.execute(insnNode, interpreter);
        yield nextFrame;
      }
    };
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

class InOutAnalysis extends ContractAnalysis {

  protected InOutAnalysis(RichControlFlow richControlFlow,
                          Direction direction,
                          boolean[] resultOrigins,
                          boolean stable,
                          ExpandableArray<State> pending) {
    super(richControlFlow, direction, resultOrigins, stable, pending);
  }

  @Override
  boolean handleReturn(Frame<BasicValue> frame, int opcode, boolean unsure) throws AnalyzerException {
    if (interpreter.deReferenced) {
      return true;
    }
    return switch (opcode) {
      case ARETURN, IRETURN, LRETURN, FRETURN, DRETURN, RETURN -> {
        BasicValue stackTop = popValue(frame);
        Result subResult;
        if (FalseValue == stackTop) {
          subResult = Value.False;
        }
        else if (TrueValue == stackTop) {
          subResult = Value.True;
        }
        else if (NullValue == stackTop) {
          subResult = Value.Null;
        }
        else if (stackTop instanceof NotNullValue) {
          subResult = Value.NotNull;
        }
        else if (stackTop instanceof ParamValue) {
          subResult = inValue;
        }
        else if (stackTop instanceof CallResultValue) {
          Set<EKey> keys = ((CallResultValue)stackTop).inters;
          subResult = new Pending(new Component[]{new Component(Value.Top, keys)});
        }
        else {
          earlyResult = Value.Top;
          yield true;
        }
        internalResult = checkLimit(resultUtil.join(internalResult, subResult));
        unsureOnly &= unsure;
        if (!unsure && internalResult == Value.Top) {
          earlyResult = internalResult;
        }
        yield true;
      }
      case ATHROW -> true;
      default -> false;
    };
  }
}

class InThrowAnalysis extends ContractAnalysis {
  private BasicValue myReturnValue;
  boolean myHasNonTrivialReturn;
  private Map<State, Set<EKey>> myThrowKeys = new HashMap<>();
  private final List<State> myNextStates = new ArrayList<>();
  private Set<EKey> myFinalThrowKeys;

  protected InThrowAnalysis(RichControlFlow richControlFlow,
                            Direction direction,
                            boolean[] resultOrigins,
                            boolean stable,
                            ExpandableArray<State> pending) {
    super(richControlFlow, direction, resultOrigins, stable, pending);
  }

  @Override
  protected @NotNull Equation analyze() throws AnalyzerException {
    Equation equation = super.analyze();
    if (equation.result == Value.Top && myThrowKeys != null && myFinalThrowKeys != null && !myFinalThrowKeys.isEmpty()) {
      for (Set<EKey> value : myThrowKeys.values()) {
        updateFinalKeys(value);
      }
      return mkEquation(new Pending(new Component[]{new Component(Value.Top, myFinalThrowKeys)}));
    }
    return equation;
  }

  @Override
  void pendingPush(State st) {
    super.pendingPush(st);
    myNextStates.add(st);
  }

  @Override
  void processState(State state) throws AnalyzerException {
    myNextStates.clear();
    super.processState(state);
    updateThrowPaths(state);
  }

  private void updateThrowPaths(State state) {
    Set<EKey> throwKeys = interpreter.throwKeys;
    interpreter.throwKeys = null;
    if (myThrowKeys == null) return;
    Set<EKey> prevKeys = myThrowKeys.remove(state);
    if (myNextStates.isEmpty()) {
      updateFinalKeys(prevKeys);
      return;
    }
    if (prevKeys == null || prevKeys.isEmpty()) {
      prevKeys = throwKeys != null ? throwKeys : Collections.emptySet();
    } else {
      if (throwKeys != null && !throwKeys.isEmpty()) {
        prevKeys = new HashSet<>(prevKeys);
        prevKeys.addAll(throwKeys);
        if (prevKeys.size() > 32) {
          myThrowKeys = null;
          return;
        }
      }
    }
    for (State next : myNextStates) {
      myThrowKeys.put(next, prevKeys);
    }
  }

  private void updateFinalKeys(Set<EKey> prevKeys) {
    if (myFinalThrowKeys == null) {
      myFinalThrowKeys = new HashSet<>(prevKeys);
    } else {
      if (myFinalThrowKeys.isEmpty()) {
        myThrowKeys = null;
        return;
      }
      myFinalThrowKeys.retainAll(prevKeys);
    }
  }

  @Override
  boolean handleReturn(Frame<BasicValue> frame, int opcode, boolean unsure) {
    Result subResult;
    if (interpreter.deReferenced) {
      subResult = Value.Top;
    } else {
      switch (opcode) {
        case ARETURN, IRETURN, LRETURN, FRETURN, DRETURN -> {
          BasicValue value = frame.pop();
          if (!(value instanceof NthParamValue) && value != NullValue && value != TrueValue && value != FalseValue ||
              myReturnValue != null && !myReturnValue.equals(value)) {
            myHasNonTrivialReturn = true;
          }
          else {
            myReturnValue = value;
          }
          subResult = Value.Top;
        }
        case RETURN -> subResult = Value.Top;
        case ATHROW -> subResult = Value.Fail;
        default -> {
          return false;
        }
      }
    }
    internalResult = resultUtil.join(internalResult, subResult);
    unsureOnly &= unsure;
    if (!unsure && internalResult == Value.Top && myHasNonTrivialReturn) {
      earlyResult = internalResult;
    }
    return true;
  }
}

class InOutInterpreter extends BasicInterpreter {
  final ParamValueBasedDirection direction;
  final InsnList insns;
  final boolean[] resultOrigins;
  final boolean nullAnalysis;
  Set<EKey> throwKeys = null;

  boolean deReferenced;

  InOutInterpreter(Direction direction, InsnList insns, boolean[] resultOrigins) {
    super(Opcodes.API_VERSION);
    this.insns = insns;
    this.resultOrigins = resultOrigins;
    if (direction instanceof ParamValueBasedDirection) {
      this.direction = (ParamValueBasedDirection)direction;
      this.nullAnalysis = this.direction.inValue == Value.Null;
    } else {
      this.direction = null;
      this.nullAnalysis = false;
    }
  }

  @Override
  public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
    boolean propagate = resultOrigins[insns.indexOf(insn)];
    if (propagate || isThrowAnalysis()) {
      switch (insn.getOpcode()) {
        case ICONST_0 -> {
          return FalseValue;
        }
        case ICONST_1 -> {
          return TrueValue;
        }
        case ACONST_NULL -> {
          return NullValue;
        }
        case LDC -> {
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
        }
        case NEW -> {
          return new NotNullValue(Type.getObjectType(((TypeInsnNode)insn).desc));
        }
        default -> {
        }
      }
    }
    return super.newOperation(insn);
  }

  @Override
  public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
    boolean propagate = resultOrigins[insns.indexOf(insn)];
    switch (insn.getOpcode()) {
      case GETFIELD, ARRAYLENGTH, MONITORENTER -> {
        if (nullAnalysis && value instanceof ParamValue) {
          deReferenced = true;
        }
        return super.unaryOperation(insn, value);
      }
      case CHECKCAST -> {
        if (value instanceof ParamValue) {
          return new ParamValue(Type.getObjectType(((TypeInsnNode)insn).desc));
        }
      }
      case INSTANCEOF -> {
        if (value instanceof ParamValue) {
          return InstanceOfCheckValue;
        }
      }
      case NEWARRAY, ANEWARRAY -> {
        if (propagate) {
          return new NotNullValue(super.unaryOperation(insn, value).getType());
        }
      }
      default -> {
      }
    }
    return super.unaryOperation(insn, value);
  }

  @Override
  public BasicValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2) throws AnalyzerException {
    switch (insn.getOpcode()) {
      case IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD, PUTFIELD -> {
        if (nullAnalysis && value1 instanceof ParamValue) {
          deReferenced = true;
        }
      }
      default -> {
      }
    }
    return super.binaryOperation(insn, value1, value2);
  }

  @Override
  public BasicValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2, BasicValue value3) {
    switch (insn.getOpcode()) {
      case IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE:
        if (nullAnalysis && value1 instanceof ParamValue) {
          deReferenced = true;
        }
      default:
    }
    return null;
  }

  @Override
  public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
    int opCode = insn.getOpcode();
    int shift = opCode == INVOKESTATIC ? 0 : 1;

    switch (opCode) {
      case INVOKESPECIAL, INVOKEINTERFACE, INVOKEVIRTUAL -> {
        if (nullAnalysis && values.get(0) instanceof ParamValue) {
          deReferenced = true;
          return super.naryOperation(insn, values);
        }
      }
    }

    boolean propagate = resultOrigins[insns.indexOf(insn)];
    if (propagate || isThrowAnalysis()) {
      switch (opCode) {
        case INVOKESTATIC, INVOKESPECIAL, INVOKEVIRTUAL, INVOKEINTERFACE -> {
          boolean stable = opCode == INVOKESTATIC || opCode == INVOKESPECIAL;
          MethodInsnNode mNode = (MethodInsnNode)insn;
          Member method = new Member(mNode.owner, mNode.name, mNode.desc);
          Type retType = Type.getReturnType(mNode.desc);
          boolean isRefRetType = retType.getSort() == Type.OBJECT || retType.getSort() == Type.ARRAY;
          if (propagate && !Type.VOID_TYPE.equals(retType) || isThrowAnalysis()) {
            if (direction != null) {
              HashSet<EKey> keys = new HashSet<>();
              for (int i = shift; i < values.size(); i++) {
                Direction direction = null;
                BasicValue value = values.get(i);
                if (value instanceof ParamValue) {
                  direction = this.direction.withIndex(i - shift);
                }
                else {
                  Value constant = Value.fromBasicValue(value);
                  if (constant != null) {
                    direction = this.direction.withValue(i - shift, constant);
                  }
                }
                if (direction != null) {
                  keys.add(new EKey(method, direction, stable));
                }
              }
              if (isRefRetType) {
                keys.add(new EKey(method, isThrowAnalysis() ? Throw : Out, stable));
              }
              if (!keys.isEmpty()) {
                if (isThrowAnalysis()) {
                  throwKeys = keys;
                }
                return new CallResultValue(retType, keys);
              }
            }
            else if (isRefRetType) {
              return new CallResultValue(retType, Collections.singleton(new EKey(method, Out, stable)));
            }
          }
        }
        case INVOKEDYNAMIC -> {
          InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode)insn;
          if (LambdaIndy.from(indy) != null || ClassDataIndexer.STRING_CONCAT_FACTORY.equals(indy.bsm.getOwner())) {
            // indy producing lambda or string concatenation is never null
            return new NotNullValue(Type.getReturnType(indy.desc));
          }
        }
        case MULTIANEWARRAY -> {
          return new NotNullValue(super.naryOperation(insn, values).getType());
        }
        default -> {
        }
      }
    }
    return super.naryOperation(insn, values);
  }

  private boolean isThrowAnalysis() {
    return direction instanceof Direction.InThrow || direction == Throw;
  }
}