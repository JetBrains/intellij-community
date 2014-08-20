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

final class CombinedCall extends BasicValue {
  final Method method;
  final boolean stableCall;
  final List<? extends BasicValue> args;

  CombinedCall(Type tp, Method method, boolean stableCall, List<? extends BasicValue> args) {
    super(tp);
    this.method = method;
    this.stableCall = stableCall;
    this.args = args;
  }
}

final class NParamValue extends BasicValue {
  final int n;
  public NParamValue(Type type, int n) {
    super(type);
    this.n = n;
  }
}

final class CombinedSingleAnalysis {
  private final ControlFlowGraph controlFlow;
  private final Method method;
  private final CombinedInterpreter interpreter;
  private BasicValue returnValue;
  private boolean exception;
  private final MethodNode methodNode;

  CombinedSingleAnalysis(Method method, ControlFlowGraph controlFlow) {
    this.method = method;
    this.controlFlow = controlFlow;
    methodNode = controlFlow.methodNode;
    interpreter = new CombinedInterpreter(Type.getArgumentTypes(methodNode.desc).length);
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

  final Equation<Key, Value> notNullParamEquation(int i, boolean stable) {
    final Key key = new Key(method, new In(i, In.NOT_NULL), stable);
    final Result<Key, Value> result;
    if (interpreter.dereferenced[i]) {
      result = new Final<Key, Value>(Value.NotNull);
    }
    else {
      Set<ParamKey> calls = interpreter.callDerefs[i];
      if (calls == null || calls.isEmpty()) {
        result = new Final<Key, Value>(Value.Top);
      }
      else {
        Set<Key> keys = new HashSet<Key>();
        for (ParamKey pk: calls) {
          keys.add(new Key(pk.method, new In(pk.i, In.NOT_NULL), pk.stable));
        }
        result = new Pending<Key, Value>(new SingletonSet<Product<Key, Value>>(new Product<Key, Value>(Value.Top, keys)));
      }
    }
    return new Equation<Key, Value>(key, result);
  }

  final Equation<Key, Value> nullableParamEquation(int i, boolean stable) {
    final Key key = new Key(method, new In(i, In.NULLABLE), stable);
    final Result<Key, Value> result;
    if (interpreter.dereferenced[i] || interpreter.notNullable[i] || returnValue instanceof NParamValue && ((NParamValue)returnValue).n == i) {
      result = new Final<Key, Value>(Value.Top);
    }
    else {
      Set<ParamKey> calls = interpreter.callDerefs[i];
      if (calls == null || calls.isEmpty()) {
        result = new Final<Key, Value>(Value.Null);
      }
      else {
        Set<Product<Key, Value>> sum = new HashSet<Product<Key, Value>>();
        for (ParamKey pk: calls) {
          sum.add(new Product<Key, Value>(Value.Top, Collections.singleton(new Key(pk.method, new In(pk.i, In.NULLABLE), pk.stable))));
        }
        result = new Pending<Key, Value>(sum);
      }
    }
    return new Equation<Key, Value>(key, result);
  }

  final Equation<Key, Value> contractEquation(int i, Value inValue, boolean stable) {
    final Key key = new Key(method, new InOut(i, inValue), stable);
    final Result<Key, Value> result;
    if (exception || (inValue == Value.Null && interpreter.dereferenced[i])) {
      result = new Final<Key, Value>(Value.Bot);
    }
    else if (FalseValue == returnValue) {
      result = new Final<Key, Value>(Value.False);
    }
    else if (TrueValue == returnValue) {
      result = new Final<Key, Value>(Value.True);
    }
    else if (NullValue == returnValue) {
      result = new Final<Key, Value>(Value.Null);
    }
    else if (returnValue instanceof NotNullValue) {
      result = new Final<Key, Value>(Value.NotNull);
    }
    else if (returnValue instanceof NParamValue && ((NParamValue)returnValue).n == i) {
      result = new Final<Key, Value>(inValue);
    }
    else if (returnValue instanceof CombinedCall) {
      CombinedCall call = (CombinedCall)returnValue;
      HashSet<Key> keys = new HashSet<Key>();
      for (int argI = 0; argI < call.args.size(); argI++) {
        BasicValue arg = call.args.get(argI);
        if (arg instanceof NParamValue) {
          NParamValue npv = (NParamValue)arg;
          if (npv.n == i) {
            keys.add(new Key(call.method, new InOut(argI, inValue), call.stableCall));
          }
        }
      }
      if (ASMUtils.isReferenceType(call.getType())) {
        keys.add(new Key(call.method, new Out(), call.stableCall));
      }
      if (keys.isEmpty()) {
        result = new Final<Key, Value>(Value.Top);
      } else {
        result = new Pending<Key, Value>(new SingletonSet<Product<Key, Value>>(new Product<Key, Value>(Value.Top, keys)));
      }
    }
    else {
      result = new Final<Key, Value>(Value.Top);
    }
    return new Equation<Key, Value>(key, result);
  }

  final Equation<Key, Value> outContractEquation(boolean stable) {
    final Key key = new Key(method, new Out(), stable);
    final Result<Key, Value> result;
    if (exception) {
      result = new Final<Key, Value>(Value.Bot);
    }
    else if (FalseValue == returnValue) {
      result = new Final<Key, Value>(Value.False);
    }
    else if (TrueValue == returnValue) {
      result = new Final<Key, Value>(Value.True);
    }
    else if (NullValue == returnValue) {
      result = new Final<Key, Value>(Value.Null);
    }
    else if (returnValue instanceof NotNullValue) {
      result = new Final<Key, Value>(Value.NotNull);
    }
    else if (returnValue instanceof CombinedCall) {
      CombinedCall call = (CombinedCall)returnValue;
      Key callKey = new Key(call.method, new Out(), call.stableCall);
      Set<Key> keys = new SingletonSet<Key>(callKey);
      result = new Pending<Key, Value>(new SingletonSet<Product<Key, Value>>(new Product<Key, Value>(Value.Top, keys)));
    }
    else {
      result = new Final<Key, Value>(Value.Top);
    }
    return new Equation<Key, Value>(key, result);
  }

  final Frame<BasicValue> createStartFrame() {
    Frame<BasicValue> frame = new Frame<BasicValue>(methodNode.maxLocals, methodNode.maxStack);
    Type returnType = Type.getReturnType(methodNode.desc);
    BasicValue returnValue = Type.VOID_TYPE.equals(returnType) ? null : new BasicValue(returnType);
    frame.setReturn(returnValue);

    Type[] args = Type.getArgumentTypes(methodNode.desc);
    int local = 0;
    if ((methodNode.access & Opcodes.ACC_STATIC) == 0) {
      frame.setLocal(local++, new AbstractValues.NotNullValue(Type.getObjectType(controlFlow.className)));
    }
    for (int i = 0; i < args.length; i++) {
      BasicValue value = new NParamValue(args[i], i);
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
  final boolean[] dereferenced;
  final boolean[] notNullable;
  final Set<ParamKey>[] callDerefs;

  CombinedInterpreter(int arity) {
    dereferenced = new boolean[arity];
    notNullable = new boolean[arity];
    callDerefs = new Set[arity];
  }

  @Override
  public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
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
    return super.newOperation(insn);
  }

  @Override
  public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
    switch (insn.getOpcode()) {
      case GETFIELD:
      case ARRAYLENGTH:
      case MONITORENTER:
        if (value instanceof NParamValue) {
          dereferenced[((NParamValue)value).n] = true;
        }
        return super.unaryOperation(insn, value);
      case CHECKCAST:
        if (value instanceof NParamValue) {
          return new NParamValue(Type.getObjectType(((TypeInsnNode)insn).desc), ((NParamValue)value).n);
        }
        break;
      case NEWARRAY:
      case ANEWARRAY:
        return new NotNullValue(super.unaryOperation(insn, value).getType());
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
        if (value1 instanceof NParamValue) {
          dereferenced[((NParamValue)value1).n] = true;
        }
        break;
      case PUTFIELD:
        if (value1 instanceof NParamValue) {
          dereferenced[((NParamValue)value1).n] = true;
        }
        if (value2 instanceof NParamValue) {
          notNullable[((NParamValue)value2).n] = true;
        }
        break;
      default:
    }
    return super.binaryOperation(insn, value1, value2);
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
        if (value1 instanceof NParamValue) {
          dereferenced[((NParamValue)value1).n] = true;
        }
        break;
      case AASTORE:
        if (value1 instanceof NParamValue) {
          dereferenced[((NParamValue)value1).n] = true;
        }
        if (value3 instanceof NParamValue) {
          notNullable[((NParamValue)value3).n] = true;
        }
        break;
      default:
    }
    return super.ternaryOperation(insn, value1, value2, value3);
  }

  @Override
  public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
    int opCode = insn.getOpcode();
    int shift = opCode == INVOKESTATIC ? 0 : 1;

    switch (opCode) {
      case INVOKESPECIAL:
      case INVOKEINTERFACE:
      case INVOKEVIRTUAL:
        if (values.get(0) instanceof NParamValue) {
          dereferenced[((NParamValue)values.get(0)).n] = true;
        }
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
          if (values.get(i) instanceof NParamValue) {
            int n = ((NParamValue)values.get(i)).n;
            if (opCode == INVOKEINTERFACE) {
              notNullable[n] = true;
            }
            else {
              Set<ParamKey> npKeys = callDerefs[n];
              if (npKeys == null) {
                npKeys = new HashSet<ParamKey>();
                callDerefs[n] = npKeys;
              }
              npKeys.add(new ParamKey(method, i - shift, stable));
            }
          }
        }
        if (shift == 1) {
          values.remove(0);
        }
        return new CombinedCall(retType, method, stable, values);
      case MULTIANEWARRAY:
        return new NotNullValue(super.naryOperation(insn, values).getType());
      default:
    }
    return super.naryOperation(insn, values);
  }
}
