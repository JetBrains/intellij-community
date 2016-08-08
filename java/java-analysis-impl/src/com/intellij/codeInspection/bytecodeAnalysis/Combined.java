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
import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph;
import com.intellij.util.SingletonSet;
import com.intellij.util.containers.HashSet;
import org.jetbrains.org.objectweb.asm.Handle;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.*;
import static org.jetbrains.org.objectweb.asm.Opcodes.*;
import static com.intellij.codeInspection.bytecodeAnalysis.Direction.*;
import static com.intellij.codeInspection.bytecodeAnalysis.CombinedData.*;

// additional data structures for combined analysis
interface CombinedData {

  final class ParamKey {
    final Method method;
    final int i;
    final boolean stable;

    ParamKey(Method method, int i, boolean stable) {
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
    final Method method;
    final List<? extends BasicValue> args;
    final boolean stableCall;
    final boolean thisCall;

    TrackableCallValue(int originInsnIndex, Type tp, Method method, List<? extends BasicValue> args, boolean stableCall, boolean thisCall) {
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
  }

  final class NthParamValue extends BasicValue {
    final int n;

    public NthParamValue(Type type, int n) {
      super(type);
      this.n = n;
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
  private final Method method;
  private final CombinedInterpreter interpreter;
  private BasicValue returnValue;
  private boolean exception;
  private final MethodNode methodNode;

  CombinedAnalysis(Method method, ControlFlowGraph controlFlow) {
    this.method = method;
    this.controlFlow = controlFlow;
    methodNode = controlFlow.methodNode;
    interpreter = new CombinedInterpreter(methodNode.instructions, Type.getArgumentTypes(methodNode.desc).length);
  }

  final void analyze() throws AnalyzerException {
    Frame<BasicValue> frame = createStartFrame();
    int insnIndex = 0;

    while (true) {
      AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
      switch (insnNode.getType()) {
        case AbstractInsnNode.LABEL:
        case AbstractInsnNode.LINE:
        case AbstractInsnNode.FRAME:
          insnIndex = controlFlow.transitions[insnIndex][0];
          break;
        default:
          switch (insnNode.getOpcode()) {
            case ATHROW:
              exception = true;
              return;
            case ARETURN:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
              returnValue = frame.pop();
              return;
            case RETURN:
              // nothing to return
              return;
            default:
              frame.execute(insnNode, interpreter);
              insnIndex = controlFlow.transitions[insnIndex][0];
          }
      }
    }
  }

  final Equation notNullParamEquation(int i, boolean stable) {
    final Key key = new Key(method, new In(i, In.NOT_NULL_MASK), stable);
    final Result result;
    if (interpreter.dereferencedParams[i]) {
      result = new Final(Value.NotNull);
    }
    else {
      Set<ParamKey> calls = interpreter.parameterFlow[i];
      if (calls == null || calls.isEmpty()) {
        result = new Final(Value.Top);
      }
      else {
        Set<Key> keys = new HashSet<>();
        for (ParamKey pk: calls) {
          keys.add(new Key(pk.method, new In(pk.i, In.NOT_NULL_MASK), pk.stable));
        }
        result = new Pending(new SingletonSet<>(new Product(Value.Top, keys)));
      }
    }
    return new Equation(key, result);
  }

  final Equation nullableParamEquation(int i, boolean stable) {
    final Key key = new Key(method, new In(i, In.NULLABLE_MASK), stable);
    final Result result;
    if (interpreter.dereferencedParams[i] || interpreter.notNullableParams[i] || returnValue instanceof NthParamValue && ((NthParamValue)returnValue).n == i) {
      result = new Final(Value.Top);
    }
    else {
      Set<ParamKey> calls = interpreter.parameterFlow[i];
      if (calls == null || calls.isEmpty()) {
        result = new Final(Value.Null);
      }
      else {
        Set<Product> sum = new HashSet<>();
        for (ParamKey pk: calls) {
          sum.add(new Product(Value.Top, Collections.singleton(new Key(pk.method, new In(pk.i, In.NULLABLE_MASK), pk.stable))));
        }
        result = new Pending(sum);
      }
    }
    return new Equation(key, result);
  }

  final Equation contractEquation(int i, Value inValue, boolean stable) {
    final Key key = new Key(method, new InOut(i, inValue), stable);
    final Result result;
    if (exception || (inValue == Value.Null && interpreter.dereferencedParams[i])) {
      result = new Final(Value.Bot);
    }
    else if (FalseValue == returnValue) {
      result = new Final(Value.False);
    }
    else if (TrueValue == returnValue) {
      result = new Final(Value.True);
    }
    else if (returnValue instanceof TrackableNullValue) {
      result = new Final(Value.Null);
    }
    else if (returnValue instanceof NotNullValue || ThisValue == returnValue) {
      result = new Final(Value.NotNull);
    }
    else if (returnValue instanceof NthParamValue && ((NthParamValue)returnValue).n == i) {
      result = new Final(inValue);
    }
    else if (returnValue instanceof TrackableCallValue) {
      TrackableCallValue call = (TrackableCallValue)returnValue;
      HashSet<Key> keys = new HashSet<>();
      for (int argI = 0; argI < call.args.size(); argI++) {
        BasicValue arg = call.args.get(argI);
        if (arg instanceof NthParamValue) {
          NthParamValue npv = (NthParamValue)arg;
          if (npv.n == i) {
            keys.add(new Key(call.method, new InOut(argI, inValue), call.stableCall));
          }
        }
      }
      if (ASMUtils.isReferenceType(call.getType())) {
        keys.add(new Key(call.method, Out, call.stableCall));
      }
      if (keys.isEmpty()) {
        result = new Final(Value.Top);
      } else {
        result = new Pending(new SingletonSet<>(new Product(Value.Top, keys)));
      }
    }
    else {
      result = new Final(Value.Top);
    }
    return new Equation(key, result);
  }

  final Equation outContractEquation(boolean stable) {
    final Key key = new Key(method, Out, stable);
    final Result result;
    if (exception) {
      result = new Final(Value.Bot);
    }
    else if (FalseValue == returnValue) {
      result = new Final(Value.False);
    }
    else if (TrueValue == returnValue) {
      result = new Final(Value.True);
    }
    else if (returnValue instanceof TrackableNullValue) {
      result = new Final(Value.Null);
    }
    else if (returnValue instanceof NotNullValue || returnValue == ThisValue) {
      result = new Final(Value.NotNull);
    }
    else if (returnValue instanceof TrackableCallValue) {
      TrackableCallValue call = (TrackableCallValue)returnValue;
      Key callKey = new Key(call.method, Out, call.stableCall);
      Set<Key> keys = new SingletonSet<>(callKey);
      result = new Pending(new SingletonSet<>(new Product(Value.Top, keys)));
    }
    else {
      result = new Final(Value.Top);
    }
    return new Equation(key, result);
  }

  final Equation nullableResultEquation(boolean stable) {
    final Key key = new Key(method, NullableOut, stable);
    final Result result;
    if (exception ||
        returnValue instanceof Trackable && interpreter.dereferencedValues[((Trackable)returnValue).getOriginInsnIndex()]) {
      result = new Final(Value.Bot);
    }
    else if (returnValue instanceof TrackableCallValue) {
      TrackableCallValue call = (TrackableCallValue)returnValue;
      Key callKey = new Key(call.method, NullableOut, call.stableCall || call.thisCall);
      Set<Key> keys = new SingletonSet<>(callKey);
      result = new Pending(new SingletonSet<>(new Product(Value.Null, keys)));
    }
    else if (returnValue instanceof TrackableNullValue) {
      result = new Final(Value.Null);
    }
    else {
      result = new Final(Value.Bot);
    }
    return new Equation(key, result);
  }

  final Frame<BasicValue> createStartFrame() {
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
  private final InsnList insns;

  CombinedInterpreter(InsnList insns, int arity) {
    dereferencedParams = new boolean[arity];
    notNullableParams = new boolean[arity];
    parameterFlow = new Set[arity];
    this.insns = insns;
    dereferencedValues = new boolean[insns.size()];
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
    switch (insn.getOpcode()) {
      case ICONST_0:
        return FalseValue;
      case ICONST_1:
        return TrueValue;
      case ACONST_NULL:
        return new TrackableNullValue(origin);
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
    return track(origin, super.newOperation(insn));
  }

  @Override
  public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
    int origin = insnIndex(insn);
    switch (insn.getOpcode()) {
      case GETFIELD:
      case ARRAYLENGTH:
      case MONITORENTER:
        if (value instanceof NthParamValue) {
          dereferencedParams[((NthParamValue)value).n] = true;
        }
        if (value instanceof Trackable) {
          dereferencedValues[((Trackable)value).getOriginInsnIndex()] = true;
        }
        return track(origin, super.unaryOperation(insn, value));
      case CHECKCAST:
        if (value instanceof NthParamValue) {
          return new NthParamValue(Type.getObjectType(((TypeInsnNode)insn).desc), ((NthParamValue)value).n);
        }
        break;
      case NEWARRAY:
      case ANEWARRAY:
        return new NotNullValue(super.unaryOperation(insn, value).getType());
      default:
    }
    return track(origin, super.unaryOperation(insn, value));
  }

  @Override
  public BasicValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2) throws AnalyzerException {
    switch (insn.getOpcode()) {
      case PUTFIELD:
        if (value1 instanceof NthParamValue) {
          dereferencedParams[((NthParamValue)value1).n] = true;
        }
        if (value1 instanceof Trackable) {
          dereferencedValues[((Trackable)value1).getOriginInsnIndex()] = true;
        }
        if (value2 instanceof NthParamValue) {
          notNullableParams[((NthParamValue)value2).n] = true;
        }
        break;
      case IALOAD:
      case LALOAD:
      case FALOAD:
      case DALOAD:
      case AALOAD:
      case BALOAD:
      case CALOAD:
      case SALOAD:
        if (value1 instanceof NthParamValue) {
          dereferencedParams[((NthParamValue)value1).n] = true;
        }
        if (value1 instanceof Trackable) {
          dereferencedValues[((Trackable)value1).getOriginInsnIndex()] = true;
        }
        break;
      default:
    }
    return track(insnIndex(insn), super.binaryOperation(insn, value1, value2));
  }

  @Override
  public BasicValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2, BasicValue value3)
    throws AnalyzerException {
    switch (insn.getOpcode()) {
      case IASTORE:
      case LASTORE:
      case FASTORE:
      case DASTORE:
      case BASTORE:
      case CASTORE:
      case SASTORE:
        if (value1 instanceof NthParamValue) {
          dereferencedParams[((NthParamValue)value1).n] = true;
        }
        if (value1 instanceof Trackable) {
          dereferencedValues[((Trackable)value1).getOriginInsnIndex()] = true;
        }
        break;
      case AASTORE:
        if (value1 instanceof NthParamValue) {
          dereferencedParams[((NthParamValue)value1).n] = true;
        }
        if (value1 instanceof Trackable) {
          dereferencedValues[((Trackable)value1).getOriginInsnIndex()] = true;
        }
        if (value3 instanceof NthParamValue) {
          notNullableParams[((NthParamValue)value3).n] = true;
        }
        break;
      default:
    }
    return null;
  }

  @Override
  public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
    int opCode = insn.getOpcode();
    int shift = opCode == INVOKESTATIC ? 0 : 1;
    int origin = insnIndex(insn);
    switch (opCode) {
      case INVOKESPECIAL:
      case INVOKEINTERFACE:
      case INVOKEVIRTUAL:
        BasicValue receiver = values.get(0);
        if (receiver instanceof NthParamValue) {
          dereferencedParams[((NthParamValue)receiver).n] = true;
        }
        if (receiver instanceof Trackable) {
          dereferencedValues[((Trackable)receiver).getOriginInsnIndex()] = true;
        }
      default:
    }

    switch (opCode) {
      case INVOKESTATIC:
      case INVOKESPECIAL:
      case INVOKEVIRTUAL:
      case INVOKEINTERFACE:
        boolean stable = opCode == INVOKESTATIC || opCode == INVOKESPECIAL;
        MethodInsnNode mNode = (MethodInsnNode)insn;
        Method method = new Method(mNode.owner, mNode.name, mNode.desc);
        Type retType = Type.getReturnType(mNode.desc);

        for (int i = shift; i < values.size(); i++) {
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
              npKeys.add(new ParamKey(method, i - shift, stable));
            }
          }
        }
        BasicValue receiver = null;
        if (shift == 1) {
          receiver = values.remove(0);
        }
        boolean thisCall = (opCode == INVOKEINTERFACE || opCode == INVOKEVIRTUAL) && receiver == ThisValue;
        return new TrackableCallValue(origin, retType, method, values, stable, thisCall);
      case MULTIANEWARRAY:
        return new NotNullValue(super.naryOperation(insn, values).getType());
      default:
    }
    return track(origin, super.naryOperation(insn, values));
  }
}

class NegationAnalysisFailure extends Exception {

}

final class NegationAnalysis {

  private final ControlFlowGraph controlFlow;
  private final Method method;
  private final NegationInterpreter interpreter;
  private final MethodNode methodNode;

  private TrackableCallValue conditionValue;
  private BasicValue trueBranchValue;
  private BasicValue falseBranchValue;

  NegationAnalysis(Method method, ControlFlowGraph controlFlow) {
    this.method = method;
    this.controlFlow = controlFlow;
    methodNode = controlFlow.methodNode;
    interpreter = new NegationInterpreter(methodNode.instructions);
  }

  private static void checkAssertion(boolean assertion) throws NegationAnalysisFailure {
    if (!assertion) {
      throw new NegationAnalysisFailure();
    }
  }

  final void analyze() throws AnalyzerException, NegationAnalysisFailure {
    Frame<BasicValue> frame = createStartFrame();
    int insnIndex = 0;

    while (true) {
      AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
      switch (insnNode.getType()) {
        case AbstractInsnNode.LABEL:
        case AbstractInsnNode.LINE:
        case AbstractInsnNode.FRAME:
          insnIndex = controlFlow.transitions[insnIndex][0];
          break;
        default:
          switch (insnNode.getOpcode()) {
            case IFEQ:
            case IFNE:
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
            default:
              frame.execute(insnNode, interpreter);
              insnIndex = controlFlow.transitions[insnIndex][0];
          }
      }
    }
  }

  private void proceedBranch(Frame<BasicValue> startFrame, int startIndex, boolean branchValue)
    throws NegationAnalysisFailure, AnalyzerException {

    Frame<BasicValue> frame = new Frame<>(startFrame);
    int insnIndex = startIndex;

    while (true) {
      AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
      switch (insnNode.getType()) {
        case AbstractInsnNode.LABEL:
        case AbstractInsnNode.LINE:
        case AbstractInsnNode.FRAME:
          insnIndex = controlFlow.transitions[insnIndex][0];
          break;
        default:
          switch (insnNode.getOpcode()) {
            case IRETURN:
              BasicValue returnValue = frame.pop();
              if (branchValue) {
                trueBranchValue = returnValue;
              }
              else {
                falseBranchValue = returnValue;
              }
              return;
            default:
              checkAssertion(controlFlow.transitions[insnIndex].length == 1);
              frame.execute(insnNode, interpreter);
              insnIndex = controlFlow.transitions[insnIndex][0];
          }
      }
    }
  }

  final Equation contractEquation(int i, Value inValue, boolean stable) {
    final Key key = new Key(method, new InOut(i, inValue), stable);
    final Result result;
    HashSet<Key> keys = new HashSet<>();
    for (int argI = 0; argI < conditionValue.args.size(); argI++) {
      BasicValue arg = conditionValue.args.get(argI);
      if (arg instanceof NthParamValue) {
        NthParamValue npv = (NthParamValue)arg;
        if (npv.n == i) {
          keys.add(new Key(conditionValue.method, new InOut(argI, inValue), conditionValue.stableCall, true));
        }
      }
    }
    if (keys.isEmpty()) {
      result = new Final(Value.Top);
    } else {
      result = new Pending(new SingletonSet<>(new Product(Value.Top, keys)));
    }
    return new Equation(key, result);
  }

  final Frame<BasicValue> createStartFrame() {
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
    this.insns = insns;
  }

  @Override
  public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
    switch (insn.getOpcode()) {
      case ICONST_0:
        return FalseValue;
      case ICONST_1:
        return TrueValue;
      default:
        return super.newOperation(insn);
    }
  }

  @Override
  public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
    int opCode = insn.getOpcode();
    int shift = opCode == INVOKESTATIC ? 0 : 1;
    int origin = insns.indexOf(insn);

    switch (opCode) {
      case INVOKESTATIC:
      case INVOKESPECIAL:
      case INVOKEVIRTUAL:
      case INVOKEINTERFACE:
        boolean stable = opCode == INVOKESTATIC || opCode == INVOKESPECIAL;
        MethodInsnNode mNode = (MethodInsnNode)insn;
        Method method = new Method(mNode.owner, mNode.name, mNode.desc);
        Type retType = Type.getReturnType(mNode.desc);
        BasicValue receiver = null;
        if (shift == 1) {
          receiver = values.remove(0);
        }
        boolean thisCall = (opCode == INVOKEINTERFACE || opCode == INVOKEVIRTUAL) && receiver == ThisValue;
        return new TrackableCallValue(origin, retType, method, values, stable, thisCall);
      default:
        return super.naryOperation(insn, values);
    }
  }
}