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

import com.intellij.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.util.ArrayUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.Analyzer;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.jetbrains.org.objectweb.asm.tree.analysis.Interpreter;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Produces equations for inference of @Contract(pure=true) annotations.
 * Scala source at https://github.com/ilya-klyuchnikov/faba
 * Algorithm: https://github.com/ilya-klyuchnikov/faba/blob/ef1c15b4758517652e939f67099bbec0260e9e68/notes/purity.md
 */
public class PurityAnalysis {
  static final int UN_ANALYZABLE_FLAG = Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_INTERFACE;

  /**
   * @param method a method descriptor
   * @param methodNode an ASM MethodNode
   * @param stable whether a method is stable (e.g. final or declared in final class)
   * @return a purity equation or null for top result (either impure or unknown, impurity assumed)
   */
  @Nullable
  public static Equation analyze(Member method, MethodNode methodNode, boolean stable) {
    EKey key = new EKey(method, Direction.Pure, stable);
    Effects hardCodedSolution = HardCodedPurity.getInstance().getHardCodedSolution(method);
    if (hardCodedSolution != null) {
      return new Equation(key, hardCodedSolution);
    }

    if ((methodNode.access & UN_ANALYZABLE_FLAG) != 0) return null;

    DataInterpreter dataInterpreter = new DataInterpreter(methodNode);
    try {
      new Analyzer<>(dataInterpreter).analyze("this", methodNode);
    }
    catch (AnalyzerException e) {
      return null;
    }
    EffectQuantum[] quanta = dataInterpreter.effects;
    DataValue returnValue = dataInterpreter.returnValue == null ? DataValue.UnknownDataValue1 : dataInterpreter.returnValue;
    Set<EffectQuantum> effects = new HashSet<>();
    for (EffectQuantum effectQuantum : quanta) {
      if (effectQuantum != null) {
        if (effectQuantum == EffectQuantum.TopEffectQuantum) {
          return returnValue == DataValue.UnknownDataValue1 ? null : new Equation(key, new Effects(returnValue, Effects.TOP_EFFECTS));
        }
        effects.add(effectQuantum);
      }
    }
    return new Equation(key, new Effects(returnValue, effects));
  }
}

// data for data analysis
abstract class DataValue implements org.jetbrains.org.objectweb.asm.tree.analysis.Value {
  public static final DataValue[] EMPTY = new DataValue[0];

  private final int myHash;

  DataValue(int hash) {
    myHash = hash;
  }

  @Override
  public final int hashCode() {
    return myHash;
  }

  Stream<EKey> dependencies() {
    return Stream.empty();
  }

  public ContractReturnValue asContractReturnValue() {
    return ContractReturnValue.returnAny();
  }

  static final DataValue ThisDataValue = new DataValue(-1) {
    @Override
    public int getSize() {
      return 1;
    }

    @Override
    public ContractReturnValue asContractReturnValue() {
      return ContractReturnValue.returnThis();
    }

    @Override
    public String toString() {
      return "DataValue: this";
    }
  };
  static final DataValue LocalDataValue = new DataValue(-2) {
    @Override
    public int getSize() {
      return 1;
    }

    @Override
    public ContractReturnValue asContractReturnValue() {
      return ContractReturnValue.returnNew();
    }

    @Override
    public String toString() {
      return "DataValue: local";
    }
  };
  static class ParameterDataValue extends DataValue {
    static final ParameterDataValue PARAM0 = new ParameterDataValue(0);
    static final ParameterDataValue PARAM1 = new ParameterDataValue(1);
    static final ParameterDataValue PARAM2 = new ParameterDataValue(2);

    final int n;

    private ParameterDataValue(int n) {
      super(n);
      this.n = n;
    }

    @Override
    public ContractReturnValue asContractReturnValue() {
      return ContractReturnValue.returnParameter(n);
    }

    static ParameterDataValue create(int n) {
      switch (n) {
        case 0: return PARAM0;
        case 1: return PARAM1;
        case 2: return PARAM2;
        default:
          return new ParameterDataValue(n);
      }
    }

    @Override
    public int getSize() {
      return 1;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ParameterDataValue that = (ParameterDataValue)o;
      return n == that.n;
    }

    @Override
    public String toString() {
      return "DataValue: arg#" + n;
    }
  }
  static class ReturnDataValue extends DataValue {
    final EKey key;

    ReturnDataValue(EKey key) {
      super(key.hashCode());
      this.key = key;
    }

    @Override
    public int getSize() {
      return 1;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ReturnDataValue that = (ReturnDataValue)o;
      return key.equals(that.key);
    }

    @Override
    Stream<EKey> dependencies() {
      return Stream.of(key);
    }

    @Override
    public String toString() {
      return "Return of: " + key;
    }
  }
  static final DataValue OwnedDataValue = new DataValue(-3) {
    @Override
    public int getSize() {
      return 1;
    }

    @Override
    public String toString() {
      return "DataValue: owned";
    }
  };
  static final DataValue UnknownDataValue1 = new DataValue(-4) {
    @Override
    public int getSize() {
      return 1;
    }

    @Override
    public String toString() {
      return "DataValue: unknown (1-slot)";
    }
  };
  static final DataValue UnknownDataValue2 = new DataValue(-5) {
    @Override
    public int getSize() {
      return 2;
    }

    @Override
    public String toString() {
      return "DataValue: unknown (2-slot)";
    }
  };
}

abstract class EffectQuantum {
  private final int myHash;

  EffectQuantum(int hash) {
    myHash = hash;
  }

  Stream<EKey> dependencies() {
    return Stream.empty();
  }

  @Override
  public final int hashCode() {
    return myHash;
  }

  static final EffectQuantum TopEffectQuantum = new EffectQuantum(-1) {
    @Override
    public String toString() {
      return "Top";
    }
  };
  static final EffectQuantum ThisChangeQuantum = new EffectQuantum(-2) {
    @Override
    public String toString() {
      return "Changes this";
    }
  };

  static final class FieldReadQuantum extends EffectQuantum {
    final EKey key;
    public FieldReadQuantum(EKey key) {
      super(key.hashCode());
      this.key = key;
    }

    @Override
    Stream<EKey> dependencies() {
      return Stream.of(key);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass() && key == ((FieldReadQuantum)o).key;
    }

    @Override
    public String toString() {
      return "Reads field " + key;
    }
  }

  static final class ReturnChangeQuantum extends EffectQuantum {
    final EKey key;
    public ReturnChangeQuantum(EKey key) {
      super(key.hashCode());
      this.key = key;
    }

    @Override
    Stream<EKey> dependencies() {
      return Stream.of(key);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass() && key == ((ReturnChangeQuantum)o).key;
    }

    @Override
    public String toString() {
      return "Changes return value of " + key;
    }
  }

  static final class ParamChangeQuantum extends EffectQuantum {
    final int n;
    public ParamChangeQuantum(int n) {
      super(n);
      this.n = n;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o != null && getClass() == o.getClass() && n == ((ParamChangeQuantum)o).n;
    }

    @Override
    public String toString() {
      return "Changes param#" + n;
    }
  }

  static final class CallQuantum extends EffectQuantum {
    final EKey key;
    final DataValue[] data;
    final boolean isStatic;
    public CallQuantum(EKey key, DataValue[] data, boolean isStatic) {
      super((key.hashCode() * 31 + Arrays.hashCode(data)) * 31 + (isStatic ? 1 : 0));
      this.key = key;
      this.data = data;
      this.isStatic = isStatic;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      CallQuantum that = (CallQuantum)o;

      if (isStatic != that.isStatic) return false;
      if (!key.equals(that.key)) return false;
      if (!Arrays.equals(data, that.data)) return false;
      return true;
    }

    @Override
    Stream<EKey> dependencies() {
      return StreamEx.of(data).flatMap(DataValue::dependencies).prepend(key);
    }

    @Override
    public String toString() {
      return "Calls " + key;
    }
  }
}

class DataInterpreter extends Interpreter<DataValue> {
  private int called = -1;
  private final MethodNode methodNode;
  private final int shift;
  final int rangeStart;
  final int rangeEnd;
  final int arity;
  final EffectQuantum[] effects;
  DataValue returnValue = null;

  protected DataInterpreter(MethodNode methodNode) {
    super(Opcodes.API_VERSION);
    this.methodNode = methodNode;
    shift = (methodNode.access & Opcodes.ACC_STATIC) == 0 ? 2 : 1;
    arity = Type.getArgumentTypes(methodNode.desc).length;
    rangeStart = shift;
    rangeEnd = arity + shift;
    effects = new EffectQuantum[methodNode.instructions.size()];
  }

  @Override
  public DataValue newValue(Type type) {
    if (type == null) {
      return DataValue.UnknownDataValue1;
    }
    called += 1;
    if (type.toString().equals("Lthis;")) {
      return DataValue.ThisDataValue;
    } else if (called < rangeEnd && rangeStart <= called) {
      if (type == Type.VOID_TYPE) {
        return null;
      } else if (ASMUtils.isReferenceType(type)) {
        return DataValue.ParameterDataValue.create(called - shift);
      } else {
        return type.getSize() == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
      }
    } else {
      if (type == Type.VOID_TYPE) {
        return null;
      } else {
        return type.getSize() == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
      }
    }
  }

  @Override
  public DataValue newOperation(AbstractInsnNode insn) {
    switch (insn.getOpcode()) {
      case Opcodes.NEW:
        return DataValue.LocalDataValue;
      case Opcodes.LCONST_0:
      case Opcodes.LCONST_1:
      case Opcodes.DCONST_0:
      case Opcodes.DCONST_1:
        return DataValue.UnknownDataValue2;
      case Opcodes.LDC:
        Object cst = ((LdcInsnNode)insn).cst;
        int size = (cst instanceof Long || cst instanceof Double) ? 2 : 1;
        return size == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
      case Opcodes.GETSTATIC:
        FieldInsnNode fieldInsn = (FieldInsnNode)insn;
        Member method = new Member(fieldInsn.owner, fieldInsn.name, fieldInsn.desc);
        EKey key = new EKey(method, Direction.Volatile, true);
        effects[methodNode.instructions.indexOf(insn)] = new EffectQuantum.FieldReadQuantum(key);
        size = Type.getType(((FieldInsnNode)insn).desc).getSize();
        return size == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
      default:
        return DataValue.UnknownDataValue1;
    }
  }

  @Override
  public DataValue binaryOperation(AbstractInsnNode insn, DataValue value1, DataValue value2) {
    switch (insn.getOpcode()) {
      case Opcodes.LALOAD:
      case Opcodes.DALOAD:
      case Opcodes.LADD:
      case Opcodes.DADD:
      case Opcodes.LSUB:
      case Opcodes.DSUB:
      case Opcodes.LMUL:
      case Opcodes.DMUL:
      case Opcodes.LDIV:
      case Opcodes.DDIV:
      case Opcodes.LREM:
      case Opcodes.LSHL:
      case Opcodes.LSHR:
      case Opcodes.LUSHR:
      case Opcodes.LAND:
      case Opcodes.LOR:
      case Opcodes.LXOR:
        return DataValue.UnknownDataValue2;
      case Opcodes.PUTFIELD:
        final EffectQuantum effectQuantum = getChangeQuantum(value1);
        int insnIndex = methodNode.instructions.indexOf(insn);
        effects[insnIndex] = effectQuantum;
        return DataValue.UnknownDataValue1;
      default:
        return DataValue.UnknownDataValue1;
    }
  }

  @Nullable
  private static EffectQuantum getChangeQuantum(DataValue value) {
    if (value == DataValue.ThisDataValue || value == DataValue.OwnedDataValue) {
      return EffectQuantum.ThisChangeQuantum;
    }
    if (value == DataValue.LocalDataValue) {
      return null;
    }
    if (value instanceof DataValue.ParameterDataValue) {
      return new EffectQuantum.ParamChangeQuantum(((DataValue.ParameterDataValue)value).n);
    }
    if (value instanceof DataValue.ReturnDataValue) {
      return new EffectQuantum.ReturnChangeQuantum(((DataValue.ReturnDataValue)value).key);
    }
    return EffectQuantum.TopEffectQuantum;
  }

  @Override
  public DataValue copyOperation(AbstractInsnNode insn, DataValue value) {
    return value;
  }

  @Override
  public DataValue naryOperation(AbstractInsnNode insn, List<? extends DataValue> values) {
    int insnIndex = methodNode.instructions.indexOf(insn);
    int opCode = insn.getOpcode();
    switch (opCode) {
      case Opcodes.MULTIANEWARRAY:
        return DataValue.LocalDataValue;
      case Opcodes.INVOKEDYNAMIC:
        // Lambda creation (w/o invocation) and StringConcatFactory have no side-effect
        InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode)insn;
        if (LambdaIndy.from(indy) == null && !ClassDataIndexer.STRING_CONCAT_FACTORY.equals(indy.bsm.getOwner())) {
          effects[insnIndex] = EffectQuantum.TopEffectQuantum;
        }
        return (ASMUtils.getReturnSizeFast((indy).desc) == 1) ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
      case Opcodes.INVOKEVIRTUAL:
      case Opcodes.INVOKESPECIAL:
      case Opcodes.INVOKESTATIC:
      case Opcodes.INVOKEINTERFACE:
        boolean stable = opCode == Opcodes.INVOKESPECIAL || opCode == Opcodes.INVOKESTATIC;
        MethodInsnNode mNode = ((MethodInsnNode)insn);
        DataValue[] data = values.toArray(DataValue.EMPTY);
        Member method = new Member(mNode.owner, mNode.name, mNode.desc);
        EKey key = new EKey(method, Direction.Pure, stable);
        EffectQuantum quantum = new EffectQuantum.CallQuantum(key, data, opCode == Opcodes.INVOKESTATIC);
        DataValue result;
        if (ASMUtils.getReturnSizeFast(mNode.desc) == 1) {
          if (ASMUtils.isReferenceReturnType(mNode.desc)) {
            result = new DataValue.ReturnDataValue(key);
          } else {
            result = DataValue.UnknownDataValue1;
          }
        }
        else {
          result = DataValue.UnknownDataValue2;
        }
        if (HardCodedPurity.getInstance().isPureMethod(method)) {
          quantum = null;
          result = HardCodedPurity.getInstance().getReturnValueForPureMethod(method);
        }
        else if (HardCodedPurity.getInstance().isThisChangingMethod(method)) {
          DataValue receiver = ArrayUtil.getFirstElement(data);
          if (receiver == DataValue.ThisDataValue) {
            quantum = EffectQuantum.ThisChangeQuantum;
          }
          else if (receiver == DataValue.LocalDataValue || receiver == DataValue.OwnedDataValue) {
            quantum = null;
          }
          else if (receiver instanceof DataValue.ParameterDataValue) {
            quantum = new EffectQuantum.ParamChangeQuantum(((DataValue.ParameterDataValue)receiver).n);
          }
          if (HardCodedPurity.getInstance().isBuilderChainCall(method)) {
            // mostly to support string concatenation
            result = receiver;
          }
        }
        effects[insnIndex] = quantum;
        return result;
    }
    return null;
  }

  @Override
  public DataValue unaryOperation(AbstractInsnNode insn, DataValue value) {

    switch (insn.getOpcode()) {
      case Opcodes.LNEG:
      case Opcodes.DNEG:
      case Opcodes.I2L:
      case Opcodes.I2D:
      case Opcodes.L2D:
      case Opcodes.F2L:
      case Opcodes.F2D:
      case Opcodes.D2L:
        return DataValue.UnknownDataValue2;
      case Opcodes.GETFIELD:
        FieldInsnNode fieldInsn = ((FieldInsnNode)insn);
        Member method = new Member(fieldInsn.owner, fieldInsn.name, fieldInsn.desc);
        EKey key = new EKey(method, Direction.Volatile, true);
        effects[methodNode.instructions.indexOf(insn)] = new EffectQuantum.FieldReadQuantum(key);
        if (value == DataValue.ThisDataValue && HardCodedPurity.getInstance().isOwnedField(fieldInsn)) {
          return DataValue.OwnedDataValue;
        } else {
          return ASMUtils.getSizeFast(fieldInsn.desc) == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
        }
      case Opcodes.CHECKCAST:
        return value;
      case Opcodes.PUTSTATIC:
        int insnIndex = methodNode.instructions.indexOf(insn);
        effects[insnIndex] = EffectQuantum.TopEffectQuantum;
        return DataValue.UnknownDataValue1;
      case Opcodes.NEWARRAY:
      case Opcodes.ANEWARRAY:
        return DataValue.LocalDataValue;
      default:
        return DataValue.UnknownDataValue1;
    }
  }

  @Override
  public DataValue ternaryOperation(AbstractInsnNode insn, DataValue value1, DataValue value2, DataValue value3) {
    int insnIndex = methodNode.instructions.indexOf(insn);
    effects[insnIndex] = getChangeQuantum(value1);
    return DataValue.UnknownDataValue1;
  }

  @Override
  public void returnOperation(AbstractInsnNode insn, DataValue value, DataValue expected) {
    if (insn.getOpcode() == Opcodes.ARETURN) {
      if (returnValue == null) {
        returnValue = value;
      }
      else if (!returnValue.equals(value)) {
        returnValue = DataValue.UnknownDataValue1;
      }
    }
  }

  @Override
  public DataValue merge(DataValue v1, DataValue v2) {
    if (v1.equals(v2)) {
      return v1;
    } else {
      int size = Math.min(v1.getSize(), v2.getSize());
      return size == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
    }
  }
}

final class PuritySolver {
  private final HashMap<EKey, Effects> solved = new HashMap<>();
  private final HashMap<EKey, Set<EKey>> dependencies = new HashMap<>();
  private final ArrayDeque<EKey> moving = new ArrayDeque<>();
  HashMap<EKey, Effects> pending = new HashMap<>();

  void addEquation(EKey key, Effects effects) {
    Set<EKey> depKeys = effects.dependencies().collect(Collectors.toSet());

    if (depKeys.isEmpty()) {
      solved.put(key, effects);
      moving.add(key);
    } else {
      pending.put(key, effects);
      for (EKey depKey : depKeys) {
        dependencies.computeIfAbsent(depKey, k -> new HashSet<>()).add(key);
      }
    }
  }

  public Map<EKey, Effects> solve() {
    while (!moving.isEmpty()) {
      EKey key = moving.pop();
      Effects effects = solved.get(key);

      EKey[] propagateKeys;
      Effects[] propagateEffects;

      if (key.stable) {
        propagateKeys = new EKey[]{key, key.mkUnstable()};
        propagateEffects = new Effects[]{effects, effects};
      }
      else {
        propagateKeys = new EKey[]{key.mkStable(), key};
        propagateEffects = new Effects[]{effects, new Effects(DataValue.UnknownDataValue1, Effects.TOP_EFFECTS)};
      }
      for (int i = 0; i < propagateKeys.length; i++) {
        EKey pKey = propagateKeys[i];
        Effects pEffects = propagateEffects[i];
        Set<EKey> dKeys = dependencies.remove(pKey);
        if (dKeys != null) {
          for (EKey dKey : dKeys) {
            Effects dEffects = pending.remove(dKey);
            if (dEffects == null) {
              // already solved, for example, solution is top
              continue;
            }
            Set<EffectQuantum> newEffects = new HashSet<>();
            Set<EffectQuantum> delta = null;
            DataValue returnValue = substitute(dEffects.returnValue, pKey, pEffects);

            for (EffectQuantum dEffect : dEffects.effects) {
              if (dEffect instanceof EffectQuantum.CallQuantum) {
                EffectQuantum.CallQuantum call = substitute((EffectQuantum.CallQuantum)dEffect, pKey, pEffects);
                if (call.key.equals(pKey)) {
                  delta = substitute(pEffects, call.data, call.isStatic);
                  if(delta.equals(Effects.TOP_EFFECTS)) {
                    newEffects = delta;
                    break;
                  }
                  newEffects.addAll(delta);
                }
                else {
                  newEffects.add(call);
                }
                continue;
              }
              if (dEffect instanceof EffectQuantum.ReturnChangeQuantum) {
                EffectQuantum.ReturnChangeQuantum retChange = (EffectQuantum.ReturnChangeQuantum)dEffect;
                if (retChange.key.equals(pKey)) {
                  if(pEffects.returnValue != DataValue.LocalDataValue) {
                    newEffects = delta = Effects.TOP_EFFECTS;
                    break;
                  }
                  continue;
                }
              }
              if (dEffect instanceof EffectQuantum.FieldReadQuantum && ((EffectQuantum.FieldReadQuantum)dEffect).key.equals(pKey)) {
                newEffects.addAll(pEffects.effects);
                continue;
              }
              newEffects.add(dEffect);
            }

            if (Effects.TOP_EFFECTS.equals(delta) && returnValue.equals(DataValue.UnknownDataValue1)) {
              solved.put(dKey, new Effects(returnValue, Effects.TOP_EFFECTS));
              moving.push(dKey);
            }
            else {
              Effects result = new Effects(returnValue, newEffects);
              if (result.dependencies().findFirst().isPresent()) {
                pending.put(dKey, result);
              }
              else {
                solved.put(dKey, result);
                moving.push(dKey);
              }
            }
          }


        }
      }

    }
    return solved;
  }

  public void addPlainFieldEquations(Predicate<MemberDescriptor> plainByDefault) {
    for (EKey key : dependencies.keySet()) {
      if(key.getDirection() == Direction.Volatile && plainByDefault.test(key.member)) {
        // Absent fields are considered non-volatile
        solved.putIfAbsent(key, new Effects(DataValue.UnknownDataValue1, Collections.emptySet()));
        moving.add(key);
      }
    }
  }

  private static EffectQuantum.CallQuantum substitute(EffectQuantum.CallQuantum call, EKey pKey, Effects pEffects) {
    List<DataValue> list = new ArrayList<>();
    boolean same = true;
    for (DataValue value : call.data) {
      DataValue newValue = substitute(value, pKey, pEffects);
      same &= newValue.equals(value);
      list.add(newValue);
    }
    return same ? call : new EffectQuantum.CallQuantum(call.key, list.toArray(DataValue.EMPTY), call.isStatic);
  }

  private static DataValue substitute(DataValue value, EKey key, Effects effects) {
    if(value instanceof DataValue.ReturnDataValue && ((DataValue.ReturnDataValue)value).key.equals(key)) {
      return effects.returnValue == DataValue.LocalDataValue ? DataValue.LocalDataValue : DataValue.UnknownDataValue1;
    }
    return value;
  }

  private static Set<EffectQuantum> substitute(Effects effects, DataValue[] data, boolean isStatic) {
    if (effects.effects.isEmpty() || Effects.TOP_EFFECTS.equals(effects.effects)) {
      return effects.effects;
    }
    Set<EffectQuantum> newEffects = new HashSet<>(effects.effects.size());
    int shift = isStatic ? 0 : 1;
    for (EffectQuantum effect : effects.effects) {
      DataValue arg = null;
      if (effect == EffectQuantum.ThisChangeQuantum) {
        arg = data[0];
      } else if (effect instanceof EffectQuantum.ParamChangeQuantum) {
        EffectQuantum.ParamChangeQuantum paramChange = ((EffectQuantum.ParamChangeQuantum)effect);
        arg = data[paramChange.n + shift];
      }
      if (arg == null || arg == DataValue.LocalDataValue) {
        continue;
      }
      if (arg == DataValue.ThisDataValue || arg == DataValue.OwnedDataValue) {
        newEffects.add(EffectQuantum.ThisChangeQuantum);
        continue;
      }
      if (arg instanceof DataValue.ParameterDataValue) {
        newEffects.add(new EffectQuantum.ParamChangeQuantum(((DataValue.ParameterDataValue)arg).n));
        continue;
      }
      if (arg instanceof DataValue.ReturnDataValue) {
        newEffects.add(new EffectQuantum.ReturnChangeQuantum(((DataValue.ReturnDataValue)arg).key));
        continue;
      }
      return Effects.TOP_EFFECTS;
    }
    return newEffects;
  }
}