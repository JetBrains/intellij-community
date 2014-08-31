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

import com.intellij.codeInspection.bytecodeAnalysis.asm.AnalyzerExt;
import com.intellij.codeInspection.bytecodeAnalysis.asm.InterpreterExt;
import com.intellij.codeInspection.bytecodeAnalysis.asm.LiteAnalyzerExt;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.Nullable;
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
    final TIntHashSet origins;

    public LabeledNull(TIntHashSet origins) {
      super(NullType);
      this.origins = origins;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      LabeledNull that = (LabeledNull)o;
      if (!origins.equals(that.origins)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return origins.hashCode();
    }
  }

  final class Calls extends BasicValue {
    final Set<Key> keys;

    public Calls(Set<Key> keys) {
      super(CallType);
      this.keys = keys;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      Calls calls = (Calls)o;

      if (!keys.equals(calls.keys)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + keys.hashCode();
      return result;
    }
  }

  final class Constraint {
    final static Constraint EMPTY = new Constraint(Collections.EMPTY_SET, new TIntHashSet(0));

    final Set<Key> calls;
    final TIntHashSet nulls;

    public Constraint(Set<Key> calls, TIntHashSet nulls) {
      this.calls = calls;
      this.nulls = nulls;
    }
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Constraint that = (Constraint)o;

      if (!calls.equals(that.calls)) return false;
      if (!nulls.equals(that.nulls)) return false;

      return true;
    }
    @Override
    public int hashCode() {
      int result = calls.hashCode();
      result = 31 * result + nulls.hashCode();
      return result;
    }
  }

  BasicValue ThisValue = new BasicValue(ThisType);
}

class NullableMethodAnalysis {

  static Result<Key, Value> FinalNull = new Final<Key, Value>(Value.Null);
  static Result<Key, Value> FinalBot = new Final<Key, Value>(Value.Bot);
  static BasicValue lNull = new LabeledNull(new TIntHashSet(0));

  static Result<Key, Value> analyze(MethodNode methodNode, boolean[] origins, boolean jsr) throws AnalyzerException {
    InsnList insns = methodNode.instructions;
    Constraint[] data = new Constraint[insns.size()];
    NullableMethodInterpreter interpreter = new NullableMethodInterpreter(insns, origins);
    Frame<BasicValue>[] frames =
      jsr ?
      new AnalyzerExt<BasicValue, Constraint, NullableMethodInterpreter>(interpreter, data, Constraint.EMPTY).analyze("this", methodNode) :
      new LiteAnalyzerExt<BasicValue, Constraint, NullableMethodInterpreter>(interpreter, data, Constraint.EMPTY).analyze("this", methodNode);

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
      Set<Product<Key, Value>> sum = new HashSet<Product<Key, Value>>(calls.keys.size());
      for (Key key : calls.keys) {
        sum.add(new Product<Key, Value>(Value.Null, Collections.singleton(key)));
      }
      return new Pending<Key, Value>(sum);
    }
    return FinalBot;
  }

  static BasicValue combine(BasicValue v1, BasicValue v2, Constraint constraint) {
    if (v1 instanceof LabeledNull) {
      return lNull;
    }
    else if (v2 instanceof LabeledNull) {
      final TIntHashSet v2Origins = ((LabeledNull)v2).origins;
      final TIntHashSet constraintOrigins = constraint.nulls;
      final boolean[] missed = {false};
      v2Origins.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int value) {
          if (!constraintOrigins.contains(value)) {
            missed[0] = true;
            return false;
          }
          return true;
        }
      });
      if (missed[0]) {
        return lNull;
      } else {
        return v1;
      }
    }
    else if (v1 instanceof Calls) {
      if (v2 instanceof Calls) {
        Set<Key> keys = new HashSet<Key>(((Calls)v2).keys);
        keys.removeAll(constraint.calls);
        keys.addAll(((Calls)v1).keys);
        return new Calls(keys);
      } else {
        return v1;
      }
    }
    else if (v2 instanceof Calls) {
      Set<Key> keys = new HashSet<Key>(((Calls)v2).keys);
      keys.removeAll(constraint.calls);
      return new Calls(keys);
    }
    return BasicValue.REFERENCE_VALUE;
  }
}

class NullableMethodInterpreter extends BasicInterpreter implements InterpreterExt<Constraint> {
  final InsnList insns;
  final boolean[] origins;


  Constraint constraint = null;
  Set<Key> delta = null;
  TIntHashSet nullsDelta = null;

  int notNullInsn = -1;

  Set<Key> notNullCall = null;
  TIntHashSet notNullNull = null;

  NullableMethodInterpreter(InsnList insns, boolean[] origins) {
    this.insns = insns;
    this.origins = origins;
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
        return new LabeledNull(new TIntHashSet(new int[]{insnIndex}));
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
          delta = ((Calls)value).keys;
        }
        break;
      case IFNULL:
        if (value instanceof Calls) {
          notNullInsn = insns.indexOf(insn) + 1;
          notNullCall = ((Calls)value).keys;
        }
        else if (value instanceof LabeledNull) {
          notNullInsn = insns.indexOf(insn) + 1;
          notNullNull = ((LabeledNull)value).origins;
        }
        break;
      case IFNONNULL:
        if (value instanceof Calls) {
          notNullInsn = insns.indexOf(((JumpInsnNode)insn).label);
          notNullCall = ((Calls)value).keys;
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
          delta = ((Calls)value1).keys;
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
      delta = ((Calls)value1).keys;
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
          delta = ((Calls)receiver).keys;
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
        if (origins[insns.indexOf(insn)]) {
          boolean stable = (opCode == INVOKESTATIC) ||
                           (opCode == INVOKESPECIAL) ||
                           (values.get(0) == ThisValue);
          MethodInsnNode mNode = ((MethodInsnNode)insn);
          Method method = new Method(mNode.owner, mNode.name, mNode.desc);
          return new Calls(Collections.singleton(new Key(method, Direction.NullableOut, stable)));
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
        return new LabeledNull(merge(((LabeledNull)v1).origins, ((LabeledNull)v2).origins));
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
        return new Calls(merge(((Calls)v1).keys, ((Calls)v2).keys));
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
    delta = null;
    nullsDelta = null;

    notNullInsn = -1;
    notNullCall = null;
    notNullNull = null;
  }

  @Override
  public Constraint getAfterData(int insn) {
    Constraint afterData = mkAfterData();
    if (notNullInsn == insn) {
      Set<Key> calls;
      if (notNullCall != null) {
        calls = new HashSet<Key>();
        calls.addAll(afterData.calls);
        calls.addAll(notNullCall);
      } else {
        calls = afterData.calls;
      }
      final TIntHashSet nulls;
      if (notNullNull != null) {
        nulls = merge(afterData.nulls, notNullNull);
      } else {
        nulls = afterData.nulls;
      }
      return new Constraint(calls, nulls);
    }
    return afterData;
  }

  private Constraint mkAfterData() {
    if (delta == null && nullsDelta == null && notNullInsn == -1) {
      return constraint;
    }
    Set<Key> calls = merge(delta, constraint.calls);
    TIntHashSet nulls = merge(nullsDelta, constraint.nulls);
    return new Constraint(calls, nulls);
  }

  @Override
  public Constraint merge(Constraint data1, Constraint data2) {
    if (data1.equals(data2)) {
      return data1;
    } else {

      Set<Key> calls1 = data1.calls;
      Set<Key> calls2 = data2.calls;
      Set<Key> calls = calls1.equals(calls2) ? calls1 : merge(calls1, calls2);

      TIntHashSet nulls1 = data1.nulls;
      TIntHashSet nulls2 = data2.nulls;
      TIntHashSet nulls = nulls1.equals(nulls2) ? nulls1 : merge(nulls1, nulls2);

      return new Constraint(calls, nulls);
    }
  }

  static TIntHashSet merge(@Nullable TIntHashSet set1, TIntHashSet set2) {
    if (set1 == null || set1.isEmpty()) {
      return set2;
    }
    else if (set2.isEmpty()) {
      return set1;
    }
    else {
      TIntHashSet set = new TIntHashSet();
      TIntIterator iter = set1.iterator();
      while (iter.hasNext()) {
        set.add(iter.next());
      }
      iter = set2.iterator();
      while (iter.hasNext()) {
        set.add(iter.next());
      }
      return set;
    }
  }

  static Set<Key> merge(@Nullable Set<Key> set1, Set<Key> set2) {
    if (set1 == null || set1.isEmpty()) {
      return set2;
    }
    else if (set2.isEmpty()) {
      return set1;
    }
    else {
      Set<Key> set = new HashSet<Key>();
      set.addAll(set1);
      set.addAll(set2);
      return set;
    }
  }
}
