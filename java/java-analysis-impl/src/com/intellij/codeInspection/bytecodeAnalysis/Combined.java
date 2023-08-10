// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import static com.intellij.codeInspection.bytecodeAnalysis.CombinedData.*;
import static com.intellij.codeInspection.bytecodeAnalysis.Direction.*;
import static org.jetbrains.org.objectweb.asm.Opcodes.*;

// additional data structures for combined analysis
interface CombinedData {

  final class ParamKey {
    final Member method;
    final int i;
    final boolean stable;

    ParamKey(Member method, int i, boolean stable) {
      this.method = method;
      this.i = i;
      this.stable = stable;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ParamKey paramKey = (ParamKey)o;

      if (i != paramKey.i) return false;
      if (stable != paramKey.stable) return false;
      if (!method.equals(paramKey.method)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = method.hashCode();
      result = 31 * result + i;
      result = 31 * result + (stable ? 1 : 0);
      return result;
    }
  }

  // value knowing at which instruction it was created
  interface Trackable {
    int getOriginInsnIndex();
  }

  final class TrackableCallValue extends BasicValue implements Trackable {
    private final int originInsnIndex;
    final Member method;
    final List<? extends BasicValue> args;
    final boolean stableCall;
    final boolean thisCall;

    TrackableCallValue(int originInsnIndex, Type tp, Member method, List<? extends BasicValue> args, boolean stableCall, boolean thisCall) {
      super(tp);
      this.originInsnIndex = originInsnIndex;
      this.method = method;
      this.args = args;
      this.stableCall = stableCall;
      this.thisCall = thisCall;
    }

    @Override
    public int getOriginInsnIndex() {
      return originInsnIndex;
    }

    @NotNull
    Set<EKey> getKeysForParameter(int idx, ParamValueBasedDirection direction) {
      Set<EKey> keys = new HashSet<>();
      for (int argI = 0; argI < this.args.size(); argI++) {
        BasicValue arg = this.args.get(argI);
        if (arg instanceof NthParamValue npv) {
          if (npv.n == idx) {
            keys.add(new EKey(this.method, direction.withIndex(argI), this.stableCall));
          }
        }
      }
      return keys;
    }
  }

  final class TrackableNullValue extends BasicValue implements Trackable {
    static final Type NullType = Type.getObjectType("null");
    private final int originInsnIndex;
    public TrackableNullValue(int originInsnIndex) {
      super(NullType);
      this.originInsnIndex = originInsnIndex;
    }

    @Override
    public int getOriginInsnIndex() {
      return originInsnIndex;
    }
  }

  final class TrackableValue extends BasicValue implements Trackable {
    private final int originInsnIndex;

    public TrackableValue(int originInsnIndex, Type type) {
      super(type);
      this.originInsnIndex = originInsnIndex;
    }

    @Override
    public int getOriginInsnIndex() {
      return originInsnIndex;
    }
  }

  BasicValue ThisValue = new BasicValue(Type.getObjectType("java/lang/Object"));
}

// specialized class for analyzing methods without branching in single pass
final class CombinedAnalysis {

  private final ControlFlowGraph controlFlow;
  private final Member method;
  private final CombinedInterpreter interpreter;
  private BasicValue returnValue;
  private boolean exception;
  private final MethodNode methodNode;

  CombinedAnalysis(Member method, ControlFlowGraph controlFlow, Set<Member> staticFields) {
    this.method = method;
    this.controlFlow = controlFlow;
    methodNode = controlFlow.methodNode;
    interpreter = new CombinedInterpreter(methodNode.instructions, Type.getArgumentTypes(methodNode.desc).length, staticFields);
  }

  void analyze() throws AnalyzerException {
    Frame<BasicValue> frame = createStartFrame();
    int insnIndex = 0;

    while (true) {
      AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
      switch (insnNode.getType()) {
        case AbstractInsnNode.LABEL, AbstractInsnNode.LINE, AbstractInsnNode.FRAME -> insnIndex = controlFlow.transitions[insnIndex][0];
        default -> {
          switch (insnNode.getOpcode()) {
            case ATHROW -> {
              exception = true;
              return;
            }
            case ARETURN, IRETURN, LRETURN, FRETURN, DRETURN -> {
              returnValue = frame.pop();
              return;
            }
            case RETURN -> {
              // nothing to return
              return;
            }
            default -> {
              frame.execute(insnNode, interpreter);
              insnIndex = controlFlow.transitions[insnIndex][0];
            }
          }
        }
      }
    }
  }

  Equation notNullParamEquation(int i, boolean stable) {
    final EKey key = new EKey(method, new In(i, false), stable);
    final Result result;
    if (interpreter.dereferencedParams[i]) {
      result = Value.NotNull;
    }
    else {
      Set<ParamKey> calls = interpreter.parameterFlow[i];
      if (calls == null || calls.isEmpty()) {
        result = Value.Top;
      }
      else {
        Set<EKey> keys = new HashSet<>();
        for (ParamKey pk: calls) {
          keys.add(new EKey(pk.method, new In(pk.i, false), pk.stable));
        }
        result = new Pending(Collections.singleton(new Component(Value.Top, keys)));
      }
    }
    return new Equation(key, result);
  }

  Equation nullableParamEquation(int i, boolean stable) {
    final EKey key = new EKey(method, new In(i, true), stable);
    final Result result;
    if (interpreter.dereferencedParams[i] || interpreter.notNullableParams[i] || returnValue instanceof NthParamValue && ((NthParamValue)returnValue).n == i) {
      result = Value.Top;
    }
    else {
      Set<ParamKey> calls = interpreter.parameterFlow[i];
      if (calls == null || calls.isEmpty()) {
        result = Value.Null;
      }
      else {
        Set<Component> sum = new HashSet<>();
        for (ParamKey pk: calls) {
          sum.add(new Component(Value.Top, Collections.singleton(new EKey(pk.method, new In(pk.i, true), pk.stable))));
        }
        result = new Pending(sum);
      }
    }
    return new Equation(key, result);
  }

  @Nullable Equation contractEquation(int i, Value inValue, boolean stable) {
    final InOut direction = new InOut(i, inValue);
    final EKey key = new EKey(method, direction, stable);
    final Result result;
    if (exception || (inValue == Value.Null && interpreter.dereferencedParams[i])) {
      result = Value.Bot;
    }
    else if (FalseValue == returnValue) {
      result = Value.False;
    }
    else if (TrueValue == returnValue) {
      result = Value.True;
    }
    else if (returnValue instanceof TrackableNullValue) {
      result = Value.Null;
    }
    else if (returnValue instanceof NotNullValue || ThisValue == returnValue) {
      result = Value.NotNull;
    }
    else if (returnValue instanceof NthParamValue && ((NthParamValue)returnValue).n == i) {
      result = inValue;
    }
    else if (returnValue instanceof TrackableCallValue call) {
      Set<EKey> keys = call.getKeysForParameter(i, direction);
      if (ASMUtils.isReferenceType(call.getType())) {
        keys.add(new EKey(call.method, Out, call.stableCall));
      }
      if (keys.isEmpty()) {
        return null;
      } else {
        result = new Pending(Collections.singleton(new Component(Value.Top, keys)));
      }
    }
    else {
      return null;
    }
    return new Equation(key, result);
  }

  @Nullable Equation failEquation(boolean stable) {
    final EKey key = new EKey(method, Throw, stable);
    final Result result;
    if (exception) {
      result = Value.Fail;
    }
    else if (!interpreter.calls.isEmpty()) {
      Set<EKey> keys = new HashSet<>();
      for (TrackableCallValue call : interpreter.calls) {
        keys.add(new EKey(call.method, Throw, call.stableCall));
      }
      result = new Pending(Collections.singleton(new Component(Value.Top, keys)));
    }
    else {
      return null;
    }
    return new Equation(key, result);
  }

  @Nullable Equation failEquation(int i, Value inValue, boolean stable) {
    final InThrow direction = new InThrow(i, inValue);
    final EKey key = new EKey(method, direction, stable);
    final Result result;
    if (exception) {
      result = Value.Fail;
    }
    else if (!interpreter.calls.isEmpty()) {
      Set<EKey> keys = new HashSet<>();
      for (TrackableCallValue call : interpreter.calls) {
        keys.addAll(call.getKeysForParameter(i, direction));
        keys.add(new EKey(call.method, Throw, call.stableCall));
      }
      result = new Pending(Collections.singleton(new Component(Value.Top, keys)));
    }
    else {
      return null;
    }
    return new Equation(key, result);
  }

  @Nullable Equation outContractEquation(boolean stable) {
    return outEquation(exception, method, returnValue, stable);
  }

  List<Equation> staticFieldEquations() {
    return EntryStream.of(interpreter.staticFields)
      .removeValues(v -> v == BasicValue.UNINITIALIZED_VALUE)
      .mapKeyValue((field, value) -> outEquation(exception, field, value, true))
      .nonNull()
      .toList();
  }

  @Nullable
  private static Equation outEquation(boolean exception, Member member, BasicValue returnValue, boolean stable) {
    final EKey key = new EKey(member, Out, stable);
    final Result result;
    if (exception) {
      result = Value.Bot;
    }
    else if (FalseValue == returnValue) {
      result = Value.False;
    }
    else if (TrueValue == returnValue) {
      result = Value.True;
    }
    else if (returnValue instanceof TrackableNullValue) {
      result = Value.Null;
    }
    else if (returnValue instanceof NotNullValue || returnValue == ThisValue) {
      result = Value.NotNull;
    }
    else if (returnValue instanceof TrackableCallValue call) {
      EKey callKey = new EKey(call.method, Out, call.stableCall);
      Set<EKey> keys = Collections.singleton(callKey);
      result = new Pending(Collections.singleton(new Component(Value.Top, keys)));
    }
    else {
      return null;
    }
    return new Equation(key, result);
  }

  Equation nullableResultEquation(boolean stable) {
    final EKey key = new EKey(method, NullableOut, stable);
    final Result result;
    if (exception ||
        returnValue instanceof Trackable && interpreter.dereferencedValues[((Trackable)returnValue).getOriginInsnIndex()]) {
      result = Value.Bot;
    }
    else if (returnValue instanceof TrackableCallValue call) {
      EKey callKey = new EKey(call.method, NullableOut, call.stableCall || call.thisCall);
      Set<EKey> keys = Collections.singleton(callKey);
      result = new Pending(Collections.singleton(new Component(Value.Null, keys)));
    }
    else if (returnValue instanceof TrackableNullValue) {
      result = Value.Null;
    }
    else {
      result = Value.Bot;
    }
    return new Equation(key, result);
  }

  Frame<BasicValue> createStartFrame() {
    Frame<BasicValue> frame = new Frame<>(methodNode.maxLocals, methodNode.maxStack);
    Type returnType = Type.getReturnType(methodNode.desc);
    BasicValue returnValue = Type.VOID_TYPE.equals(returnType) ? null : new BasicValue(returnType);
    frame.setReturn(returnValue);

    Type[] args = Type.getArgumentTypes(methodNode.desc);
    int local = 0;
    if ((methodNode.access & ACC_STATIC) == 0) {
      frame.setLocal(local++, ThisValue);
    }
    for (int i = 0; i < args.length; i++) {
      BasicValue value = new NthParamValue(args[i], i);
      frame.setLocal(local++, value);
      if (args[i].getSize() == 2) {
        frame.setLocal(local++, BasicValue.UNINITIALIZED_VALUE);
      }
    }
    while (local < methodNode.maxLocals) {
      frame.setLocal(local++, BasicValue.UNINITIALIZED_VALUE);
    }
    return frame;
  }
}

final class CombinedInterpreter extends BasicInterpreter {
  // Parameters dereferenced during execution of a method, tracked by parameter's indices.
  // Dereferenced parameters are @NotNull.
  final boolean[] dereferencedParams;
  // Parameters, that are written to something or passed to an interface methods.
  // This parameters cannot be @Nullable.
  final boolean[] notNullableParams;
  // parameterFlow(i) for i-th parameter stores a set parameter positions it is passed to
  // parameter is @NotNull if any of its usages are @NotNull
  final Set<ParamKey>[] parameterFlow;

  // Trackable values that were dereferenced during execution of a method
  // Values are are identified by `origin` index
  final boolean[] dereferencedValues;

  final List<TrackableCallValue> calls = new ArrayList<>();

  final Map<Member, BasicValue> staticFields;

  private final InsnList insns;

  CombinedInterpreter(InsnList insns,
                      int arity,
                      Set<Member> staticFields) {
    super(Opcodes.API_VERSION);
    dereferencedParams = new boolean[arity];
    notNullableParams = new boolean[arity];
    parameterFlow = new Set[arity];
    this.insns = insns;
    dereferencedValues = new boolean[insns.size()];
    this.staticFields = StreamEx.of(staticFields).cross(BasicValue.UNINITIALIZED_VALUE).toMap();
  }

  private int insnIndex(AbstractInsnNode insn) {
    return insns.indexOf(insn);
  }

  private static BasicValue track(int origin, BasicValue basicValue) {
    return basicValue == null ? null : new TrackableValue(origin, basicValue.getType());
  }

  @Override
  public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
    int origin = insnIndex(insn);
    return switch (insn.getOpcode()) {
      case ICONST_0 -> FalseValue;
      case ICONST_1 -> TrueValue;
      case ACONST_NULL -> new TrackableNullValue(origin);
      case LDC -> {
        Object cst = ((LdcInsnNode)insn).cst;
        if (cst instanceof Type type) {
          if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
            yield CLASS_VALUE;
          }
          if (type.getSort() == Type.METHOD) {
            yield METHOD_VALUE;
          }
        }
        else if (cst instanceof String) {
          yield STRING_VALUE;
        }
        else if (cst instanceof Handle) {
          yield METHOD_HANDLE_VALUE;
        }
        yield track(origin, super.newOperation(insn));
      }
      case NEW -> new NotNullValue(Type.getObjectType(((TypeInsnNode)insn).desc));
      default -> track(origin, super.newOperation(insn));
    };
  }

  @Override
  public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
    int origin = insnIndex(insn);
    switch (insn.getOpcode()) {
      case GETFIELD, ARRAYLENGTH, MONITORENTER -> {
        if (value instanceof NthParamValue) {
          dereferencedParams[((NthParamValue)value).n] = true;
        }
        if (value instanceof Trackable) {
          dereferencedValues[((Trackable)value).getOriginInsnIndex()] = true;
        }
        return track(origin, super.unaryOperation(insn, value));
      }
      case PUTSTATIC -> {
        if (!staticFields.isEmpty()) {
          FieldInsnNode node = (FieldInsnNode)insn;
          Member field = new Member(node.owner, node.name, node.desc);
          staticFields.computeIfPresent(field, (f, v) -> value);
        }
      }
      case CHECKCAST -> {
        if (value instanceof NthParamValue) {
          return new NthParamValue(Type.getObjectType(((TypeInsnNode)insn).desc), ((NthParamValue)value).n);
        }
      }
      case NEWARRAY, ANEWARRAY -> {
        return new NotNullValue(super.unaryOperation(insn, value).getType());
      }
      default -> {
      }
    }
    return track(origin, super.unaryOperation(insn, value));
  }

  @Override
  public BasicValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2) throws AnalyzerException {
    switch (insn.getOpcode()) {
      case PUTFIELD -> {
        if (value1 instanceof NthParamValue) {
          dereferencedParams[((NthParamValue)value1).n] = true;
        }
        if (value1 instanceof Trackable) {
          dereferencedValues[((Trackable)value1).getOriginInsnIndex()] = true;
        }
        if (value2 instanceof NthParamValue) {
          notNullableParams[((NthParamValue)value2).n] = true;
        }
      }
      case IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD -> {
        if (value1 instanceof NthParamValue) {
          dereferencedParams[((NthParamValue)value1).n] = true;
        }
        if (value1 instanceof Trackable) {
          dereferencedValues[((Trackable)value1).getOriginInsnIndex()] = true;
        }
      }
    }
    return track(insnIndex(insn), super.binaryOperation(insn, value1, value2));
  }

  @Override
  public BasicValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2, BasicValue value3) {
    switch (insn.getOpcode()) {
      case IASTORE, LASTORE, FASTORE, DASTORE, BASTORE, CASTORE, SASTORE -> {
        if (value1 instanceof NthParamValue) {
          dereferencedParams[((NthParamValue)value1).n] = true;
        }
        if (value1 instanceof Trackable) {
          dereferencedValues[((Trackable)value1).getOriginInsnIndex()] = true;
        }
      }
      case AASTORE -> {
        if (value1 instanceof NthParamValue) {
          dereferencedParams[((NthParamValue)value1).n] = true;
        }
        if (value1 instanceof Trackable) {
          dereferencedValues[((Trackable)value1).getOriginInsnIndex()] = true;
        }
        if (value3 instanceof NthParamValue) {
          notNullableParams[((NthParamValue)value3).n] = true;
        }
      }
    }
    return null;
  }

  @Override
  public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
    int opCode = insn.getOpcode();
    int origin = insnIndex(insn);

    switch (opCode) {
      case INVOKESTATIC, INVOKESPECIAL, INVOKEVIRTUAL, INVOKEINTERFACE -> {
        MethodInsnNode mNode = (MethodInsnNode)insn;
        Member method = new Member(mNode.owner, mNode.name, mNode.desc);
        TrackableCallValue value = methodCall(opCode, origin, method, values);
        calls.add(value);
        return value;
      }
      case INVOKEDYNAMIC -> {
        InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode)insn;
        if (ClassDataIndexer.STRING_CONCAT_FACTORY.equals(indy.bsm.getOwner())) {
          return new NotNullValue(Type.getReturnType(indy.desc));
        }
        LambdaIndy lambda = LambdaIndy.from(indy);
        if (lambda == null) break;
        int targetOpCode = lambda.getAssociatedOpcode();
        if (targetOpCode == -1) break;
        methodCall(targetOpCode, origin, lambda.getMethod(), lambda.getLambdaMethodArguments(values, this::newValue));
        return new NotNullValue(lambda.getFunctionalInterfaceType());
      }
      case MULTIANEWARRAY -> {
        return new NotNullValue(super.naryOperation(insn, values).getType());
      }
      default -> {
      }
    }
    return track(origin, super.naryOperation(insn, values));
  }

  @NotNull
  private TrackableCallValue methodCall(int opCode, int origin, Member method, List<? extends BasicValue> values) {
    Type retType = Type.getReturnType(method.methodDesc);
    boolean stable = opCode == INVOKESTATIC || opCode == INVOKESPECIAL;
    boolean thisCall = false;
    if (opCode != INVOKESTATIC) {
      BasicValue receiver = values.remove(0);
      if (receiver instanceof NthParamValue) {
        dereferencedParams[((NthParamValue)receiver).n] = true;
      }
      if (receiver instanceof Trackable) {
        dereferencedValues[((Trackable)receiver).getOriginInsnIndex()] = true;
      }
      thisCall = receiver == ThisValue;
    }

    for (int i = 0; i < values.size(); i++) {
      if (values.get(i) instanceof NthParamValue) {
        int n = ((NthParamValue)values.get(i)).n;
        if (opCode == INVOKEINTERFACE) {
          notNullableParams[n] = true;
        }
        else {
          Set<ParamKey> npKeys = parameterFlow[n];
          if (npKeys == null) {
            npKeys = new HashSet<>();
            parameterFlow[n] = npKeys;
          }
          npKeys.add(new ParamKey(method, i, stable));
        }
      }
    }
    return new TrackableCallValue(origin, retType, method, values, stable, thisCall);
  }
}

class NegationAnalysisFailedException extends Exception {

}

final class NegationAnalysis {

  private final ControlFlowGraph controlFlow;
  private final Member method;
  private final NegationInterpreter interpreter;
  private final MethodNode methodNode;

  private TrackableCallValue conditionValue;
  private BasicValue trueBranchValue;
  private BasicValue falseBranchValue;

  NegationAnalysis(Member method, ControlFlowGraph controlFlow) {
    this.method = method;
    this.controlFlow = controlFlow;
    methodNode = controlFlow.methodNode;
    interpreter = new NegationInterpreter(methodNode.instructions);
  }

  private static void checkAssertion(boolean assertion) throws NegationAnalysisFailedException {
    if (!assertion) {
      throw new NegationAnalysisFailedException();
    }
  }

  void analyze() throws AnalyzerException, NegationAnalysisFailedException {
    Frame<BasicValue> frame = createStartFrame();
    int insnIndex = 0;

    while (true) {
      AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
      switch (insnNode.getType()) {
        case AbstractInsnNode.LABEL, AbstractInsnNode.LINE, AbstractInsnNode.FRAME ->
          insnIndex = controlFlow.transitions[insnIndex][0];
        default -> {
          switch (insnNode.getOpcode()) {
            case IFEQ, IFNE -> {
              BasicValue conValue = popValue(frame);
              checkAssertion(conValue instanceof TrackableCallValue);
              frame.execute(insnNode, interpreter);
              conditionValue = (TrackableCallValue)conValue;
              int jumpIndex = methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
              int nextIndex = insnIndex + 1;
              proceedBranch(frame, jumpIndex, IFNE == insnNode.getOpcode());
              proceedBranch(frame, nextIndex, IFEQ == insnNode.getOpcode());
              checkAssertion(FalseValue == trueBranchValue);
              checkAssertion(TrueValue == falseBranchValue);
              return;
            }
            default -> {
              frame.execute(insnNode, interpreter);
              insnIndex = controlFlow.transitions[insnIndex][0];
            }
          }
        }
      }
    }
  }

  private void proceedBranch(Frame<BasicValue> startFrame, int startIndex, boolean branchValue)
    throws NegationAnalysisFailedException, AnalyzerException {

    Frame<BasicValue> frame = new Frame<>(startFrame);
    int insnIndex = startIndex;

    while (true) {
      AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
      switch (insnNode.getType()) {
        case AbstractInsnNode.LABEL, AbstractInsnNode.LINE, AbstractInsnNode.FRAME -> insnIndex = controlFlow.transitions[insnIndex][0];
        default -> {
          if (insnNode.getOpcode() == IRETURN) {
            BasicValue returnValue = frame.pop();
            if (branchValue) {
              trueBranchValue = returnValue;
            }
            else {
              falseBranchValue = returnValue;
            }
            return;
          }
          else {
            checkAssertion(controlFlow.transitions[insnIndex].length == 1);
            frame.execute(insnNode, interpreter);
            insnIndex = controlFlow.transitions[insnIndex][0];
          }
        }
      }
    }
  }

  Equation contractEquation(int i, Value inValue, boolean stable) {
    final EKey key = new EKey(method, new InOut(i, inValue), stable);
    final Result result;
    HashSet<EKey> keys = new HashSet<>();
    for (int argI = 0; argI < conditionValue.args.size(); argI++) {
      BasicValue arg = conditionValue.args.get(argI);
      if (arg instanceof NthParamValue npv && npv.n == i) {
        keys.add(new EKey(conditionValue.method, new InOut(argI, inValue), conditionValue.stableCall, true));
      }
    }
    if (keys.isEmpty()) {
      result = Value.Top;
    } else {
      result = new Pending(Collections.singleton(new Component(Value.Top, keys)));
    }
    return new Equation(key, result);
  }

  Frame<BasicValue> createStartFrame() {
    Frame<BasicValue> frame = new Frame<>(methodNode.maxLocals, methodNode.maxStack);
    Type returnType = Type.getReturnType(methodNode.desc);
    BasicValue returnValue = Type.VOID_TYPE.equals(returnType) ? null : new BasicValue(returnType);
    frame.setReturn(returnValue);

    Type[] args = Type.getArgumentTypes(methodNode.desc);
    int local = 0;
    if ((methodNode.access & ACC_STATIC) == 0) {
      frame.setLocal(local++, ThisValue);
    }
    for (int i = 0; i < args.length; i++) {
      BasicValue value = new NthParamValue(args[i], i);
      frame.setLocal(local++, value);
      if (args[i].getSize() == 2) {
        frame.setLocal(local++, BasicValue.UNINITIALIZED_VALUE);
      }
    }
    while (local < methodNode.maxLocals) {
      frame.setLocal(local++, BasicValue.UNINITIALIZED_VALUE);
    }
    return frame;
  }

  private static BasicValue popValue(Frame<BasicValue> frame) {
    return frame.getStack(frame.getStackSize() - 1);
  }
}

final class NegationInterpreter extends BasicInterpreter {
  private final InsnList insns;

  NegationInterpreter(InsnList insns) {
    super(Opcodes.API_VERSION);
    this.insns = insns;
  }

  @Override
  public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
    return switch (insn.getOpcode()) {
      case ICONST_0 -> FalseValue;
      case ICONST_1 -> TrueValue;
      default -> super.newOperation(insn);
    };
  }

  @Override
  public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
    int opCode = insn.getOpcode();
    int shift = opCode == INVOKESTATIC ? 0 : 1;
    int origin = insns.indexOf(insn);

    return switch (opCode) {
      case INVOKESTATIC, INVOKESPECIAL, INVOKEVIRTUAL, INVOKEINTERFACE -> {
        boolean stable = opCode == INVOKESTATIC || opCode == INVOKESPECIAL;
        MethodInsnNode mNode = (MethodInsnNode)insn;
        Member method = new Member(mNode.owner, mNode.name, mNode.desc);
        Type retType = Type.getReturnType(mNode.desc);
        BasicValue receiver = null;
        if (shift == 1) {
          receiver = values.remove(0);
        }
        boolean thisCall = (opCode == INVOKEINTERFACE || opCode == INVOKEVIRTUAL) && receiver == ThisValue;
        yield new TrackableCallValue(origin, retType, method, values, stable, thisCall);
      }
      default -> super.naryOperation(insn, values);
    };
  }
}