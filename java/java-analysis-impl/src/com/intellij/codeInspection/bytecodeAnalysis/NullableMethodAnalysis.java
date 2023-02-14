// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import com.intellij.codeInspection.bytecodeAnalysis.asm.AnalyzerExt;
import com.intellij.codeInspection.bytecodeAnalysis.asm.InterpreterExt;
import com.intellij.codeInspection.bytecodeAnalysis.asm.LiteAnalyzerExt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.Opcodes;
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

final class LabeledNull extends BasicValue {
  private static final Type NullType = Type.getObjectType("null");

  final int origins;

  LabeledNull(int origins) {
    super(NullType);
    this.origins = origins;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LabeledNull that = (LabeledNull)o;
    return origins == that.origins;
  }

  @Override
  public int hashCode() {
    return origins;
  }
}

final class Calls extends BasicValue {
  private static final Type CallType = Type.getObjectType("/Call");

  final int mergedLabels;

  Calls(int mergedLabels) {
    super(CallType);
    this.mergedLabels = mergedLabels;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    Calls calls = (Calls)o;
    return mergedLabels == calls.mergedLabels;
  }

  @Override
  public int hashCode() {
    return mergedLabels;
  }
}

final class Constraint {
  final static Constraint EMPTY = new Constraint(0, 0);

  final int calls;
  final int nulls;

  Constraint(int calls, int nulls) {
    this.calls = calls;
    this.nulls = nulls;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Constraint that = (Constraint)o;

    if (calls != that.calls) return false;
    if (nulls != that.nulls) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = calls;
    result = 31 * result + nulls;
    return result;
  }
}

final class NullableMethodAnalysis {
  private static final BasicValue lNull = new LabeledNull(0);

  static Result analyze(MethodNode methodNode, boolean[] origins, boolean jsr) throws AnalyzerException {
    InsnList insns = methodNode.instructions;
    Constraint[] data = new Constraint[insns.size()];
    int[] originsMapping = mapOrigins(origins);

    NullableMethodInterpreter interpreter = new NullableMethodInterpreter(insns, origins, originsMapping);
    Frame<BasicValue>[] frames =
      jsr ?
      new AnalyzerExt<>(interpreter, data, Constraint.EMPTY).analyze("this", methodNode) :
      new LiteAnalyzerExt<>(interpreter, data, Constraint.EMPTY).analyze("this", methodNode);

    BasicValue result = BasicValue.REFERENCE_VALUE;
    for (int i = 0; i < frames.length; i++) {
      Frame<BasicValue> frame = frames[i];
      if (frame != null && insns.get(i).getOpcode() == Opcodes.ARETURN) {
        BasicValue stackTop = frame.pop();
        result = combine(result, stackTop, data[i]);
      }
    }
    if (result instanceof LabeledNull) {
      return Value.Null;
    }
    if (result instanceof Calls calls) {
      int mergedMappedLabels = calls.mergedLabels;
      if (mergedMappedLabels != 0) {
        Set<Component> sum = new HashSet<>();
        EKey[] createdKeys = interpreter.keys;
        for (int origin = 0; origin < originsMapping.length; origin++) {
          int mappedOrigin = originsMapping[origin];
          EKey createdKey = createdKeys[origin];
          if (createdKey != null && (mergedMappedLabels & (1 << mappedOrigin)) != 0) {
            sum.add(new Component(Value.Null, Collections.singleton(createdKey)));
          }
        }
        if (!sum.isEmpty()) {
          return new Pending(sum);
        }
      }
    }
    return Value.Bot;
  }

  private static int @NotNull [] mapOrigins(boolean[] origins) {
    int[] originsMapping = new int[origins.length];
    int mapped = 0;
    for (int i = 0; i < origins.length; i++) {
      originsMapping[i] = origins[i] ? mapped++ : -1;
    }
    return originsMapping;
  }

  static BasicValue combine(BasicValue v1, BasicValue v2, Constraint constraint) {
    if (v1 instanceof LabeledNull) {
      return lNull;
    }
    else if (v2 instanceof LabeledNull) {
      int v2Origins = ((LabeledNull)v2).origins;
      int constraintOrigins = constraint.nulls;
      int intersect = v2Origins & constraintOrigins;
      return intersect == v2Origins ? v1 : lNull;
    }
    else if (v1 instanceof Calls calls1) {
      if (v2 instanceof Calls calls2) {
        int labels2 = calls2.mergedLabels;
        int aliveLabels2 = labels2 - (labels2 & constraint.calls);
        return new Calls(calls1.mergedLabels | aliveLabels2);
      } else {
        return v1;
      }
    }
    else if (v2 instanceof Calls) {
      Calls calls2 = (Calls)v2;
      int labels2 = calls2.mergedLabels;
      int aliveLabels2 = labels2 - (labels2 & constraint.calls);
      return new Calls(aliveLabels2);
    }
    return BasicValue.REFERENCE_VALUE;
  }
}

class NullableMethodInterpreter extends BasicInterpreter implements InterpreterExt<Constraint> {
  private final InsnList insns;
  private final boolean[] origins;
  private final int[] originsMapping;
  final EKey[] keys;

  Constraint constraint;
  int delta;
  int nullsDelta;
  int notNullInsn = -1;
  int notNullCall;
  int notNullNull;

  NullableMethodInterpreter(InsnList insns, boolean[] origins, int[] originsMapping) {
    super(Opcodes.API_VERSION);
    this.insns = insns;
    this.origins = origins;
    this.originsMapping = originsMapping;
    keys = new EKey[originsMapping.length];
  }

  @Override
  public BasicValue newValue(Type type) {
    return ASMUtils.isThisType(type) ? ASMUtils.THIS_VALUE : super.newValue(type);
  }

  @Override
  public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
    if (insn.getOpcode() == Opcodes.ACONST_NULL) {
      int insnIndex = insns.indexOf(insn);
      if (origins[insnIndex]) {
        return new LabeledNull(1 << originsMapping[insnIndex]);
      }
    }
    return super.newOperation(insn);
  }

  @Override
  public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
    switch (insn.getOpcode()) {
      case GETFIELD, ARRAYLENGTH, MONITORENTER -> {
        if (value instanceof Calls) {
          delta = ((Calls)value).mergedLabels;
        }
      }
      case IFNULL -> {
        if (value instanceof Calls) {
          notNullInsn = insns.indexOf(insn) + 1;
          notNullCall = ((Calls)value).mergedLabels;
        }
        else if (value instanceof LabeledNull) {
          notNullInsn = insns.indexOf(insn) + 1;
          notNullNull = ((LabeledNull)value).origins;
        }
      }
      case IFNONNULL -> {
        if (value instanceof Calls) {
          notNullInsn = insns.indexOf(((JumpInsnNode)insn).label);
          notNullCall = ((Calls)value).mergedLabels;
        }
        else if (value instanceof LabeledNull) {
          notNullInsn = insns.indexOf(((JumpInsnNode)insn).label);
          notNullNull = ((LabeledNull)value).origins;
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
      case PUTFIELD, IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD -> {
        if (value1 instanceof Calls) {
          delta = ((Calls)value1).mergedLabels;
        }
        if (value1 instanceof LabeledNull) {
          nullsDelta = ((LabeledNull)value1).origins;
        }
      }
      default -> { }
    }
    return super.binaryOperation(insn, value1, value2);
  }

  @Override
  public BasicValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2, BasicValue value3) {
    if (value1 instanceof Calls) {
      delta = ((Calls)value1).mergedLabels;
    }
    if (value1 instanceof LabeledNull){
      nullsDelta = ((LabeledNull)value1).origins;
    }
    return null;
  }

  @Override
  public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
    int opCode = insn.getOpcode();
    switch (opCode) {
      case INVOKESPECIAL, INVOKEINTERFACE, INVOKEVIRTUAL -> {
        BasicValue receiver = values.get(0);
        if (receiver instanceof Calls calls) {
          delta = calls.mergedLabels;
        }
        if (receiver instanceof LabeledNull labeledNull) {
          nullsDelta = labeledNull.origins;
        }
      }
      default -> { }
    }

    switch (opCode) {
      case INVOKESTATIC, INVOKESPECIAL, INVOKEVIRTUAL -> {
        int insnIndex = insns.indexOf(insn);
        if (origins[insnIndex]) {
          boolean stable = opCode == INVOKESTATIC || opCode == INVOKESPECIAL;
          MethodInsnNode mNode = ((MethodInsnNode)insn);
          Member method = new Member(mNode.owner, mNode.name, mNode.desc);
          int label = 1 << originsMapping[insnIndex];
          if (keys[insnIndex] == null) {
            keys[insnIndex] = new EKey(method, Direction.NullableOut, stable);
          }
          return new Calls(label);
        }
      }
      default -> { }
    }
    return super.naryOperation(insn, values);
  }

  @Override
  public BasicValue merge(BasicValue v1, BasicValue v2) {
    if (v1 instanceof LabeledNull) {
      if (v2 instanceof LabeledNull) {
        return new LabeledNull(((LabeledNull)v1).origins | ((LabeledNull)v2).origins);
      }
      else {
        return v1;
      }
    }
    else if (v2 instanceof LabeledNull) {
      return v2;
    }
    else if (v1 instanceof Calls calls1) {
      if (v2 instanceof Calls calls2) {
        return new Calls(calls1.mergedLabels | calls2.mergedLabels);
      }
      else {
        return v1;
      }
    }
    else if (v2 instanceof Calls) {
      return v2;
    }
    return super.merge(v1, v2);
  }

  // ---------- InterpreterExt<Constraint> --------------

  @Override
  public void init(Constraint previous) {
    constraint = previous;
    delta = 0;
    nullsDelta = 0;

    notNullInsn = -1;
    notNullCall = 0;
    notNullNull = 0;
  }

  @Override
  public Constraint getAfterData(int insn) {
    Constraint afterData = mkAfterData();
    if (notNullInsn == insn) {
      return new Constraint(afterData.calls | notNullCall, afterData.nulls | notNullNull);
    }
    return afterData;
  }

  private Constraint mkAfterData() {
    if (delta == 0 && nullsDelta == 0 && notNullInsn == -1) {
      return constraint;
    }
    return new Constraint(constraint.calls | delta, constraint.nulls | nullsDelta);
  }

  @Override
  public Constraint merge(Constraint data1, Constraint data2) {
    return data1.equals(data2) ? data1 : new Constraint(data1.calls | data2.calls, data1.nulls | data2.nulls);
  }
}