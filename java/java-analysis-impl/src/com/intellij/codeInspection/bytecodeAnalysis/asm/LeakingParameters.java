// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import org.jetbrains.annotations.NotNull;
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
  public final Frame<? extends Value>[] frames;
  public final boolean[] parameters;
  public final boolean[] nullableParameters;

  public LeakingParameters(Frame<? extends Value>[] frames, boolean[] parameters, boolean[] nullableParameters) {
    this.frames = frames;
    this.parameters = parameters;
    this.nullableParameters = nullableParameters;
  }

  @NotNull
  public static LeakingParameters build(String className, MethodNode methodNode, boolean jsr) throws AnalyzerException {
    Frame<ParamsValue>[] frames = jsr ? new Analyzer<>(new ParametersUsage(methodNode)).analyze(className, methodNode)
                                      : new LiteAnalyzer<>(new ParametersUsage(methodNode)).analyze(className, methodNode);
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
    return new LeakingParameters(frames, notNullParameters, nullableParameters);
  }

  @NotNull
  public static LeakingParameters buildFast(String className, MethodNode methodNode, boolean jsr) throws AnalyzerException {
    IParametersUsage parametersUsage = new IParametersUsage(methodNode);
    Frame<?>[] frames = jsr ? new Analyzer<>(parametersUsage).analyze(className, methodNode)
                            : new LiteAnalyzer<>(parametersUsage).analyze(className, methodNode);
    int leakingMask = parametersUsage.leaking;
    int nullableLeakingMask = parametersUsage.nullableLeaking;
    boolean[] notNullParameters = new boolean[parametersUsage.arity];
    boolean[] nullableParameters = new boolean[parametersUsage.arity];
    for (int i = 0; i < notNullParameters.length; i++) {
      notNullParameters[i] = (leakingMask & (1 << i)) != 0;
      nullableParameters[i] = ((leakingMask | nullableLeakingMask) & (1 << i)) != 0;
    }
    return new LeakingParameters(frames, notNullParameters, nullableParameters);
  }
}

final class ParamsValue implements Value {
  final boolean[] params;
  final int size;

  ParamsValue(boolean @NotNull [] params, int size) {
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
    if (!(o instanceof ParamsValue)) return false;
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
    // size == -1 means bottom (uninitialized) value
    return size == -1 ? 1 : size;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IParamsValue)) return false;
    IParamsValue that = (IParamsValue)o;
    return (this.size == that.size && this.params == that.params);
  }

  @Override
  public int hashCode() {
    return 31 * params + size;
  }
}

class ParametersUsage extends Interpreter<ParamsValue> {
  private int param = -1;
  final int arity;
  final ParamsValue val1;
  final ParamsValue val2;

  ParametersUsage(MethodNode methodNode) {
    super(API_VERSION);
    arity = Type.getArgumentTypes(methodNode.desc).length;
    boolean[] emptyParams = new boolean[arity];
    val1 = new ParamsValue(emptyParams, 1);
    val2 = new ParamsValue(emptyParams, 2);
  }

  @Override
  public ParamsValue newParameterValue(boolean isInstanceMethod, int local, Type type) {
    param++;
    int n = isInstanceMethod ? param - 1 : param;
    if (n >= 0 && (ASMUtils.isReferenceType(type) || ASMUtils.isBooleanType(type))) {
      boolean[] params = new boolean[arity];
      params[n] = true;
      return new ParamsValue(params, type.getSize());
    }
    return newValue(type);
  }

  @Override
  public ParamsValue newValue(Type type) {
    if (type == null) return val1;
    if (type == Type.VOID_TYPE) return null;
    return type.getSize() == 1 ? val1 : val2;
  }

  @Override
  public ParamsValue newOperation(final AbstractInsnNode insn) {
    int size;
    switch (insn.getOpcode()) {
      case LCONST_0, LCONST_1, DCONST_0, DCONST_1 -> size = 2;
      case LDC -> {
        Object cst = ((LdcInsnNode)insn).cst;
        size = cst instanceof Long || cst instanceof Double ? 2 : 1;
      }
      case GETSTATIC -> size = Type.getType(((FieldInsnNode)insn).desc).getSize();
      default -> size = 1;
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
      case CHECKCAST -> {
        return value;
      }
      case LNEG, DNEG, I2L, I2D, L2D, F2L, F2D, D2L -> size = 2;
      case GETFIELD -> size = Type.getType(((FieldInsnNode)insn).desc).getSize();
      default -> size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public ParamsValue binaryOperation(AbstractInsnNode insn, ParamsValue value1, ParamsValue value2) {
    int size = switch (insn.getOpcode()) {
      case LALOAD, DALOAD, LADD, DADD, LSUB, DSUB, LMUL, DMUL, LDIV, DDIV, LREM, DREM, LSHL, LSHR, LUSHR, LAND, LOR, LXOR -> 2;
      default -> 1;
    };
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
    }
    else {
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
  static final IParamsValue none = new IParamsValue(0, -1);

  private int param = -1;
  final int arity;
  int leaking;
  int nullableLeaking;

  IParametersUsage(MethodNode methodNode) {
    super(API_VERSION);
    arity = Type.getArgumentTypes(methodNode.desc).length;
  }

  @Override
  public IParamsValue newParameterValue(boolean isInstanceMethod, int local, Type type) {
    param++;
    int n = isInstanceMethod ? param - 1 : param;
    if (n >= 0 && (ASMUtils.isReferenceType(type) || ASMUtils.isBooleanType(type))) {
      return new IParamsValue(1 << n, type.getSize());
    }
    return newValue(type);
  }

  @Override
  public IParamsValue newValue(Type type) {
    if (type == null) return none;
    if (type == Type.VOID_TYPE) return null;
    return type.getSize() == 1 ? val1 : val2;
  }

  @Override
  public IParamsValue newOperation(final AbstractInsnNode insn) {
    int size = switch (insn.getOpcode()) {
      case LCONST_0, LCONST_1, DCONST_0, DCONST_1 -> 2;
      case LDC -> {
        Object cst = ((LdcInsnNode)insn).cst;
        yield cst instanceof Long || cst instanceof Double ? 2 : 1;
      }
      case GETSTATIC -> ASMUtils.getSizeFast(((FieldInsnNode)insn).desc);
      default -> 1;
    };
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
      case CHECKCAST -> {
        return value;
      }
      case LNEG, DNEG, I2L, I2D, L2D, F2L, F2D, D2L -> size = 2;
      case GETFIELD -> {
        size = ASMUtils.getSizeFast(((FieldInsnNode)insn).desc);
        leaking |= value.params;
      }
      case ARRAYLENGTH, MONITORENTER, INSTANCEOF, IRETURN, ARETURN, IFNONNULL, IFNULL, IFEQ, IFNE -> {
        size = 1;
        leaking |= value.params;
      }
      default -> size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public IParamsValue binaryOperation(AbstractInsnNode insn, IParamsValue value1, IParamsValue value2) {
    int size;
    switch (insn.getOpcode()) {
      case LALOAD, DALOAD -> {
        size = 2;
        leaking |= value1.params;
      }
      case LADD, DADD, LSUB, DSUB, LMUL, DMUL, LDIV, DDIV, LREM, DREM, LSHL, LSHR, LUSHR, LAND, LOR, LXOR -> size = 2;
      case IALOAD, FALOAD, AALOAD, BALOAD, CALOAD, SALOAD -> {
        leaking |= value1.params;
        size = 1;
      }
      case PUTFIELD -> {
        leaking |= value1.params;
        nullableLeaking |= value2.params;
        size = 1;
      }
      default -> size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public IParamsValue ternaryOperation(AbstractInsnNode insn, IParamsValue value1, IParamsValue value2, IParamsValue value3) {
    switch (insn.getOpcode()) {
      case IASTORE, LASTORE, FASTORE, DASTORE, BASTORE, CASTORE, SASTORE -> leaking |= value1.params;
      case AASTORE -> {
        leaking |= value1.params;
        nullableLeaking |= value3.params;
      }
      default -> {
      }
    }
    return null;
  }

  @Override
  public IParamsValue naryOperation(AbstractInsnNode insn, List<? extends IParamsValue> values) {
    int opcode = insn.getOpcode();
    switch (opcode) {
      case INVOKESTATIC, INVOKESPECIAL, INVOKEVIRTUAL, INVOKEINTERFACE, INVOKEDYNAMIC -> {
        for (IParamsValue value : values) {
          leaking |= value.params;
        }
      }
      default -> {
      }
    }
    int size;
    if (opcode == MULTIANEWARRAY) {
      size = 1;
    }
    else {
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
      case GETFIELD, ARRAYLENGTH, MONITORENTER, INSTANCEOF, IRETURN, ARETURN, IFNONNULL, IFNULL, IFEQ, IFNE -> {
        boolean[] params = value.params;
        for (int i = 0; i < arity; i++) {
          leaking[i] |= params[i];
        }
      }
      default -> {
      }
    }
    return super.unaryOperation(insn, value);
  }

  @Override
  public ParamsValue binaryOperation(AbstractInsnNode insn, ParamsValue value1, ParamsValue value2) {
    switch (insn.getOpcode()) {
      case IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD -> {
        boolean[] params = value1.params;
        for (int i = 0; i < arity; i++) {
          leaking[i] |= params[i];
        }
      }
      case PUTFIELD -> {
        boolean[] params = value1.params;
        for (int i = 0; i < arity; i++) {
          leaking[i] |= params[i];
        }
        params = value2.params;
        for (int i = 0; i < arity; i++) {
          nullableLeaking[i] |= params[i];
        }
      }
      default -> {}
    }
    return super.binaryOperation(insn, value1, value2);
  }

  @Override
  public ParamsValue ternaryOperation(AbstractInsnNode insn, ParamsValue value1, ParamsValue value2, ParamsValue value3) {
    boolean[] params;
    switch (insn.getOpcode()) {
      case IASTORE, LASTORE, FASTORE, DASTORE, BASTORE, CASTORE, SASTORE -> {
        params = value1.params;
        for (int i = 0; i < arity; i++) {
          leaking[i] |= params[i];
        }
      }
      case AASTORE -> {
        params = value1.params;
        for (int i = 0; i < arity; i++) {
          leaking[i] |= params[i];
        }
        params = value3.params;
        for (int i = 0; i < arity; i++) {
          nullableLeaking[i] |= params[i];
        }
      }
      default -> {}
    }
    return null;
  }

  @Override
  public ParamsValue naryOperation(AbstractInsnNode insn, List<? extends ParamsValue> values) {
    switch (insn.getOpcode()) {
      case INVOKESTATIC, INVOKESPECIAL, INVOKEVIRTUAL, INVOKEINTERFACE -> {
        for (ParamsValue value : values) {
          boolean[] params = value.params;
          for (int i = 0; i < arity; i++) {
            leaking[i] |= params[i];
          }
        }
      }
      default -> {
      }
    }
    return super.naryOperation(insn, values);
  }
}