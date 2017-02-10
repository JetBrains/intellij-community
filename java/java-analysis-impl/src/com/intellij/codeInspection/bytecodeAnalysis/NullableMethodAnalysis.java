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

import com.intellij.codeInspection.bytecodeAnalysis.asm.AnalyzerExt;
import com.intellij.codeInspection.bytecodeAnalysis.asm.InterpreterExt;
import com.intellij.codeInspection.bytecodeAnalysis.asm.LiteAnalyzerExt;
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

import static com.intellij.codeInspection.bytecodeAnalysis.NullableMethodAnalysisData.*;

interface NullableMethodAnalysisData {
  Type NullType = Type.getObjectType("null");
  Type ThisType = Type.getObjectType("this");
  Type CallType = Type.getObjectType("/Call");

  final class LabeledNull extends BasicValue {
    final int origins;

    public LabeledNull(int origins) {
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
    final int mergedLabels;

    public Calls(int mergedLabels) {
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

    public Constraint(int calls, int nulls) {
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

  BasicValue ThisValue = new BasicValue(ThisType);
}

class NullableMethodAnalysis {

  static Result FinalNull = new Final(Value.Null);
  static Result FinalBot = new Final(Value.Bot);
  static BasicValue lNull = new LabeledNull(0);

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
      return FinalNull;
    }
    if (result instanceof Calls) {
      Calls calls = ((Calls)result);
      int mergedMappedLabels = calls.mergedLabels;
      if (mergedMappedLabels != 0) {
        Set<Product> sum = new HashSet<>();
        Key[] createdKeys = interpreter.keys;
        for (int origin = 0; origin < originsMapping.length; origin++) {
          int mappedOrigin = originsMapping[origin];
          Key createdKey = createdKeys[origin];
          if (createdKey != null && (mergedMappedLabels & (1 << mappedOrigin)) != 0) {
            sum.add(new Product(Value.Null, Collections.singleton(createdKey)));
          }
        }
        if (!sum.isEmpty()) {
          return new Pending(sum);
        }
      }
    }
    return FinalBot;
  }

  private static int[] mapOrigins(boolean[] origins) {
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
    else if (v1 instanceof Calls) {
      if (v2 instanceof Calls) {
        Calls calls1 = (Calls)v1;
        Calls calls2 = (Calls)v2;
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
  final InsnList insns;
  final boolean[] origins;
  private final int[] originsMapping;
  final Key[] keys;

  Constraint constraint;
  int delta;
  int nullsDelta;
  int notNullInsn = -1;
  int notNullCall;
  int notNullNull;

  NullableMethodInterpreter(InsnList insns, boolean[] origins, int[] originsMapping) {
    this.insns = insns;
    this.origins = origins;
    this.originsMapping = originsMapping;
    keys = new Key[originsMapping.length];
  }

  @Override
  public BasicValue newValue(Type type) {
    return ThisType.equals(type) ? ThisValue : super.newValue(type);
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
      case GETFIELD:
      case ARRAYLENGTH:
      case MONITORENTER:
        if (value instanceof Calls) {
          delta = ((Calls)value).mergedLabels;
        }
        break;
      case IFNULL:
        if (value instanceof Calls) {
          notNullInsn = insns.indexOf(insn) + 1;
          notNullCall = ((Calls)value).mergedLabels;
        }
        else if (value instanceof LabeledNull) {
          notNullInsn = insns.indexOf(insn) + 1;
          notNullNull = ((LabeledNull)value).origins;
        }
        break;
      case IFNONNULL:
        if (value instanceof Calls) {
          notNullInsn = insns.indexOf(((JumpInsnNode)insn).label);
          notNullCall = ((Calls)value).mergedLabels;
        }
        else if (value instanceof LabeledNull) {
          notNullInsn = insns.indexOf(((JumpInsnNode)insn).label);
          notNullNull = ((LabeledNull)value).origins;
        }
        break;
      default:

    }
    return super.unaryOperation(insn, value);
  }

  @Override
  public BasicValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2) throws AnalyzerException {
    switch (insn.getOpcode()) {
      case PUTFIELD:
      case IALOAD:
      case LALOAD:
      case FALOAD:
      case DALOAD:
      case AALOAD:
      case BALOAD:
      case CALOAD:
      case SALOAD:
        if (value1 instanceof Calls) {
          delta = ((Calls)value1).mergedLabels;
        }
        if (value1 instanceof LabeledNull){
          nullsDelta = ((LabeledNull)value1).origins;
        }
        break;
      default:
    }
    return super.binaryOperation(insn, value1, value2);
  }

  @Override
  public BasicValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2, BasicValue value3)
    throws AnalyzerException {
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
      case INVOKESPECIAL:
      case INVOKEINTERFACE:
      case INVOKEVIRTUAL:
        BasicValue receiver = values.get(0);
        if (receiver instanceof Calls) {
          delta = ((Calls)receiver).mergedLabels;
        }
        if (receiver instanceof LabeledNull){
          nullsDelta = ((LabeledNull)receiver).origins;
        }
        break;
      default:
    }

    switch (opCode) {
      case INVOKESTATIC:
      case INVOKESPECIAL:
      case INVOKEVIRTUAL:
        int insnIndex = insns.indexOf(insn);
        if (origins[insnIndex]) {
          boolean stable = opCode == INVOKESTATIC || opCode == INVOKESPECIAL;
          MethodInsnNode mNode = ((MethodInsnNode)insn);
          Method method = new Method(mNode.owner, mNode.name, mNode.desc);
          int label = 1 << originsMapping[insnIndex];
          if (keys[insnIndex] == null) {
            keys[insnIndex] = new Key(method, Direction.NullableOut, stable);
          }
          return new Calls(label);
        }
        break;
      default:
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
    else if (v1 instanceof Calls) {
      if (v2 instanceof Calls) {
        Calls calls1 = (Calls)v1;
        Calls calls2 = (Calls)v2;
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
    if (data1.equals(data2)) {
      return data1;
    } else {
      return new Constraint(data1.calls | data2.calls, data1.nulls | data2.nulls);
    }
  }
}
