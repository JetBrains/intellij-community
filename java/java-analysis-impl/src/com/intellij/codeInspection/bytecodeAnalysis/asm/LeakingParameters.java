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
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.*;

import java.util.Arrays;
import java.util.List;

import static org.jetbrains.org.objectweb.asm.Opcodes.*;

/**
 * @author lambdamix
 */
public class LeakingParameters {
  public final Frame<Value>[] frames;
  public final boolean[] parameters;
  public final boolean[] nullableParameters;

  public LeakingParameters(Frame<Value>[] frames, boolean[] parameters, boolean[] nullableParameters) {
    this.frames = frames;
    this.parameters = parameters;
    this.nullableParameters = nullableParameters;
  }

  @NotNull
  public static LeakingParameters build(String className, MethodNode methodNode, boolean jsr) throws AnalyzerException {
    Frame<ParamsValue>[] frames = jsr ?
                                  new Analyzer<>(new ParametersUsage(methodNode)).analyze(className, methodNode) :
                                  new LiteAnalyzer<>(new ParametersUsage(methodNode)).analyze(className, methodNode);
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
            new Frame<>(frame).execute(insnNode, collector);
        }
      }
    }
    boolean[] notNullParameters = collector.leaking;
    boolean[] nullableParameters = collector.nullableLeaking;
    for (int i = 0; i < nullableParameters.length; i++) {
      nullableParameters[i] |= notNullParameters[i];
    }
    return new LeakingParameters((Frame<Value>[])(Frame<?>[])frames, notNullParameters, nullableParameters);
  }

  @NotNull
  public static LeakingParameters buildFast(String className, MethodNode methodNode, boolean jsr) throws AnalyzerException {
    IParametersUsage parametersUsage = new IParametersUsage(methodNode);
    Frame<?>[] frames = jsr ?
                        new Analyzer<>(parametersUsage).analyze(className, methodNode) :
                        new LiteAnalyzer<>(parametersUsage).analyze(className, methodNode);
    int leakingMask = parametersUsage.leaking;
    int nullableLeakingMask = parametersUsage.nullableLeaking;
    boolean[] notNullParameters = new boolean[parametersUsage.arity];
    boolean[] nullableParameters = new boolean[parametersUsage.arity];
    for (int i = 0; i < notNullParameters.length; i++) {
      notNullParameters[i] = (leakingMask & (1 << i)) != 0;
      nullableParameters[i] = ((leakingMask | nullableLeakingMask) & (1 << i)) != 0;
    }
    return new LeakingParameters((Frame<Value>[])frames, notNullParameters, nullableParameters);
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

// specialized version
final class IParamsValue implements Value {
  final int params;
  final int size;

  IParamsValue(int params, int size) {
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
    IParamsValue that = (IParamsValue)o;
    return (this.size == that.size && this.params == that.params);
  }

  @Override
  public int hashCode() {
    return 31 * params + size;
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
    super(API_VERSION);
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
    if (called < rangeEnd && rangeStart <= called && (ASMUtils.isReferenceType(type) || ASMUtils.isBooleanType(type))) {
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
        return value;
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
    return null;
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

class IParametersUsage extends Interpreter<IParamsValue> {
  static final IParamsValue val1 = new IParamsValue(0, 1);
  static final IParamsValue val2 = new IParamsValue(0, 2);
  int leaking;
  int nullableLeaking;
  int called = -1;
  final int rangeStart;
  final int rangeEnd;
  final int arity;
  final int shift;

  IParametersUsage(MethodNode methodNode) {
    super(Opcodes.API_VERSION);
    arity = Type.getArgumentTypes(methodNode.desc).length;
    shift = (methodNode.access & ACC_STATIC) == 0 ? 2 : 1;
    rangeStart = shift;
    rangeEnd = arity + shift;
  }

  @Override
  public IParamsValue newValue(Type type) {
    if (type == null) return val1;
    called++;
    if (type == Type.VOID_TYPE) return null;
    if (called < rangeEnd && rangeStart <= called && (ASMUtils.isReferenceType(type) || ASMUtils.isBooleanType(type))) {
      int n = called - shift;
      return type.getSize() == 1 ? new IParamsValue(1 << n, 1) : new IParamsValue(1 << n, 2);
    }
    else {
      return type.getSize() == 1 ? val1 : val2;
    }
  }

  @Override
  public IParamsValue newOperation(final AbstractInsnNode insn) {
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
        size = ASMUtils.getSizeFast(((FieldInsnNode)insn).desc);
        break;
      default:
        size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public IParamsValue copyOperation(AbstractInsnNode insn, IParamsValue value) {
    return value;
  }

  @Override
  public IParamsValue unaryOperation(AbstractInsnNode insn, IParamsValue value) {
    int size;
    switch (insn.getOpcode()) {
      case CHECKCAST:
        return value;
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
        size = ASMUtils.getSizeFast(((FieldInsnNode)insn).desc);
        leaking |= value.params;
        break;
      case ARRAYLENGTH:
      case MONITORENTER:
      case INSTANCEOF:
      case IRETURN:
      case ARETURN:
      case IFNONNULL:
      case IFNULL:
      case IFEQ:
      case IFNE:
        size = 1;
        leaking |= value.params;
        break;
      default:
        size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public IParamsValue binaryOperation(AbstractInsnNode insn, IParamsValue value1, IParamsValue value2) {
    int size;
    switch (insn.getOpcode()) {
      case LALOAD:
      case DALOAD:
        size = 2;
        leaking |= value1.params;
        break;
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
      case IALOAD:
      case FALOAD:
      case AALOAD:
      case BALOAD:
      case CALOAD:
      case SALOAD:
        leaking |= value1.params;
        size = 1;
        break;
      case PUTFIELD:
        leaking |= value1.params;
        nullableLeaking |= value2.params;
        size = 1;
        break;
      default:
        size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public IParamsValue ternaryOperation(AbstractInsnNode insn, IParamsValue value1, IParamsValue value2, IParamsValue value3) {
    switch (insn.getOpcode()) {
      case IASTORE:
      case LASTORE:
      case FASTORE:
      case DASTORE:
      case BASTORE:
      case CASTORE:
      case SASTORE:
        leaking |= value1.params;
        break;
      case AASTORE:
        leaking |= value1.params;
        nullableLeaking |= value3.params;
        break;
      default:
    }
    return null;
  }

  @Override
  public IParamsValue naryOperation(AbstractInsnNode insn, List<? extends IParamsValue> values) {
    int opcode = insn.getOpcode();
    switch (opcode) {
      case INVOKESTATIC:
      case INVOKESPECIAL:
      case INVOKEVIRTUAL:
      case INVOKEINTERFACE:
        for (IParamsValue value : values) {
          leaking |= value.params;
        }
        break;
      default:
    }
    int size;
    if (opcode == MULTIANEWARRAY) {
      size = 1;
    } else {
      String desc = (opcode == INVOKEDYNAMIC) ? ((InvokeDynamicInsnNode) insn).desc : ((MethodInsnNode) insn).desc;
      size = ASMUtils.getReturnSizeFast(desc);
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public void returnOperation(AbstractInsnNode insn, IParamsValue value, IParamsValue expected) {}

  @Override
  public IParamsValue merge(IParamsValue v1, IParamsValue v2) {
    if (v1.equals(v2)) return v1;
    return new IParamsValue(v1.params | v2.params, Math.min(v1.size, v2.size));
  }
}

class LeakingParametersCollector extends ParametersUsage {
  final boolean[] leaking;
  final boolean[] nullableLeaking;
  LeakingParametersCollector(MethodNode methodNode) {
    super(methodNode);
    leaking = new boolean[arity];
    nullableLeaking = new boolean[arity];
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
        boolean[] params = value1.params;
        for (int i = 0; i < arity; i++) {
          leaking[i] |= params[i];
        }
        break;
      case PUTFIELD:
        params = value1.params;
        for (int i = 0; i < arity; i++) {
          leaking[i] |= params[i];
        }
        params = value2.params;
        for (int i = 0; i < arity; i++) {
          nullableLeaking[i] |= params[i];
        }
        break;
      default:
    }
    return super.binaryOperation(insn, value1, value2);
  }

  @Override
  public ParamsValue ternaryOperation(AbstractInsnNode insn, ParamsValue value1, ParamsValue value2, ParamsValue value3) {
    boolean[] params;
    switch (insn.getOpcode()) {
      case IASTORE:
      case LASTORE:
      case FASTORE:
      case DASTORE:
      case BASTORE:
      case CASTORE:
      case SASTORE:
        params = value1.params;
        for (int i = 0; i < arity; i++) {
          leaking[i] |= params[i];
        }
        break;
      case AASTORE:
        params = value1.params;
        for (int i = 0; i < arity; i++) {
          leaking[i] |= params[i];
        }
        params = value3.params;
        for (int i = 0; i < arity; i++) {
          nullableLeaking[i] |= params[i];
        }
        break;
      default:
    }
    return null;
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

