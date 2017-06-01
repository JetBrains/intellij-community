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
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.*;
import org.jetbrains.org.objectweb.asm.tree.analysis.Analyzer;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.jetbrains.org.objectweb.asm.tree.analysis.Interpreter;

import java.util.*;

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
  public static Equation analyze(Method method, MethodNode methodNode, boolean stable) {
    EKey key = new EKey(method, Direction.Pure, stable);
    Set<EffectQuantum> hardCodedSolution = HardCodedPurity.getInstance().getHardCodedSolution(method);
    if (hardCodedSolution != null) {
      return new Equation(key, new Effects(hardCodedSolution));
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
    Set<EffectQuantum> effects = new HashSet<>();
    for (EffectQuantum effectQuantum : quanta) {
      if (effectQuantum != null) {
        if (effectQuantum == EffectQuantum.TopEffectQuantum) return null;
        effects.add(effectQuantum);
      }
    }
    return new Equation(key, new Effects(effects));
  }
}

// data for data analysis
abstract class DataValue implements org.jetbrains.org.objectweb.asm.tree.analysis.Value {
  private final int myHash;

  DataValue(int hash) {
    myHash = hash;
  }

  @Override
  public final int hashCode() {
    return myHash;
  }

  static final DataValue ThisDataValue = new DataValue(-1) {
    @Override
    public int getSize() {
      return 1;
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
    public String toString() {
      return "DataValue: local";
    }
  };
  static class ParameterDataValue extends DataValue {
    final int n;

    ParameterDataValue(int n) {
      super(n);
      this.n = n;
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
      if (n != that.n) return false;
      return true;
    }

    @Override
    public String toString() {
      return "DataValue: arg#" + n;
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
  static class ParamChangeQuantum extends EffectQuantum {
    final int n;
    public ParamChangeQuantum(int n) {
      super(n);
      this.n = n;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ParamChangeQuantum that = (ParamChangeQuantum)o;

      if (n != that.n) return false;

      return true;
    }

    @Override
    public String toString() {
      return "Changes param#" + n;
    }
  }
  static class CallQuantum extends EffectQuantum {
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
      // Probably incorrect - comparing Object[] arrays with Arrays.equals
      if (!Arrays.equals(data, that.data)) return false;

      return true;
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
        return new DataValue.ParameterDataValue(called - shift);
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
  public DataValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
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
        size = Type.getType(((FieldInsnNode)insn).desc).getSize();
        return size == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
      default:
        return DataValue.UnknownDataValue1;
    }
  }

  @Override
  public DataValue binaryOperation(AbstractInsnNode insn, DataValue value1, DataValue value2) throws AnalyzerException {
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
        final EffectQuantum effectQuantum;
        if (value1 == DataValue.ThisDataValue || value1 == DataValue.OwnedDataValue) {
          effectQuantum = EffectQuantum.ThisChangeQuantum;
        } else if (value1 == DataValue.LocalDataValue) {
          effectQuantum = null;
        } else if (value1 instanceof DataValue.ParameterDataValue) {
          effectQuantum = new EffectQuantum.ParamChangeQuantum(((DataValue.ParameterDataValue)value1).n);
        } else {
          effectQuantum = EffectQuantum.TopEffectQuantum;
        }
        int insnIndex = methodNode.instructions.indexOf(insn);
        effects[insnIndex] = effectQuantum;
        return DataValue.UnknownDataValue1;
      default:
        return DataValue.UnknownDataValue1;
    }
  }

  @Override
  public DataValue copyOperation(AbstractInsnNode insn, DataValue value) throws AnalyzerException {
    return value;
  }

  @Override
  public DataValue naryOperation(AbstractInsnNode insn, List<? extends DataValue> values) throws AnalyzerException {
    int insnIndex = methodNode.instructions.indexOf(insn);
    int opCode = insn.getOpcode();
    switch (opCode) {
      case Opcodes.MULTIANEWARRAY:
        return DataValue.LocalDataValue;
      case Opcodes.INVOKEDYNAMIC:
        // Lambda creation (w/o invocation) has no side-effect
        if (LambdaIndy.from((InvokeDynamicInsnNode)insn) == null) {
          effects[insnIndex] = EffectQuantum.TopEffectQuantum;
        }
        return (ASMUtils.getReturnSizeFast(((InvokeDynamicInsnNode)insn).desc) == 1) ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
      case Opcodes.INVOKEVIRTUAL:
      case Opcodes.INVOKESPECIAL:
      case Opcodes.INVOKESTATIC:
      case Opcodes.INVOKEINTERFACE:
        boolean stable = opCode == Opcodes.INVOKESPECIAL || opCode == Opcodes.INVOKESTATIC;
        MethodInsnNode mNode = ((MethodInsnNode)insn);
        DataValue[] data = values.toArray(new DataValue[0]);
        Method method = new Method(mNode.owner, mNode.name, mNode.desc);
        EKey key = new EKey(method, Direction.Pure, stable);
        EffectQuantum quantum = new EffectQuantum.CallQuantum(key, data, opCode == Opcodes.INVOKESTATIC);
        DataValue result = (ASMUtils.getReturnSizeFast(mNode.desc) == 1) ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
        if (HardCodedPurity.getInstance().isPureMethod(method)) {
          quantum = null;
          result = DataValue.LocalDataValue;
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
  public DataValue unaryOperation(AbstractInsnNode insn, DataValue value) throws AnalyzerException {

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
  public DataValue ternaryOperation(AbstractInsnNode insn, DataValue value1, DataValue value2, DataValue value3) throws AnalyzerException {
    int insnIndex = methodNode.instructions.indexOf(insn);
    if (value1 == DataValue.ThisDataValue || value1 == DataValue.OwnedDataValue) {
      effects[insnIndex] = EffectQuantum.ThisChangeQuantum;
    } else if (value1 instanceof DataValue.ParameterDataValue) {
      effects[insnIndex] = new EffectQuantum.ParamChangeQuantum(((DataValue.ParameterDataValue)value1).n);
    } else if (value1 == DataValue.LocalDataValue) {
      effects[insnIndex] = null;
    } else {
      effects[insnIndex] = EffectQuantum.TopEffectQuantum;
    }
    return DataValue.UnknownDataValue1;
  }

  @Override
  public void returnOperation(AbstractInsnNode insn, DataValue value, DataValue expected) throws AnalyzerException {

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
  private static final Set<EffectQuantum> TOP_EFFECTS = Collections.singleton(EffectQuantum.TopEffectQuantum);
  private HashMap<EKey, Set<EffectQuantum>> solved = new HashMap<>();
  private HashMap<EKey, Set<EKey>> dependencies = new HashMap<>();
  private final Stack<EKey> moving = new Stack<>();
  private HashMap<EKey, Set<EffectQuantum>> pending = new HashMap<>();

  void addEquation(EKey key, Set<EffectQuantum> effects) {
    Set<EKey> callKeys = new HashSet<>();
    for (EffectQuantum effect : effects) {
      if (effect instanceof EffectQuantum.CallQuantum) {
        callKeys.add(((EffectQuantum.CallQuantum)effect).key);
      }
    }

    if (callKeys.isEmpty()) {
      solved.put(key, effects);
      moving.add(key);
    } else {
      pending.put(key, effects);
      for (EKey callKey : callKeys) {
        Set<EKey> deps = dependencies.get(callKey);
        if (deps == null) {
          deps = new HashSet<>();
          dependencies.put(callKey, deps);
        }
        deps.add(key);
      }
    }
  }

  public Map<EKey, Set<EffectQuantum>> solve() {
    while (!moving.isEmpty()) {
      EKey key = moving.pop();
      Set<EffectQuantum> effects = solved.get(key);

      EKey[] propagateKeys;
      Set[] propagateEffects;

      if (key.stable) {
        propagateKeys = new EKey[]{key, key.mkUnstable()};
        propagateEffects = new Set[]{effects, effects};
      }
      else {
        propagateKeys = new EKey[]{key.mkStable(), key};
        propagateEffects = new Set[]{effects, TOP_EFFECTS};
      }
      for (int i = 0; i < propagateKeys.length; i++) {
        EKey pKey = propagateKeys[i];
        @SuppressWarnings("unchecked")
        Set<EffectQuantum> pEffects = propagateEffects[i];
        Set<EKey> dKeys = dependencies.remove(pKey);
        if (dKeys != null) {
          for (EKey dKey : dKeys) {
            Set<EffectQuantum> dEffects = pending.remove(dKey);
            if (dEffects == null) {
              // already solved, for example, solution is top
              continue;
            }
            Set<EKey> callKeys = new HashSet<>();
            Set<EffectQuantum> newEffects = new HashSet<>();
            Set<EffectQuantum> delta = null;

            for (EffectQuantum dEffect : dEffects) {
              if (dEffect instanceof EffectQuantum.CallQuantum) {
                EffectQuantum.CallQuantum call = ((EffectQuantum.CallQuantum)dEffect);
                if (call.key.equals(pKey)) {
                  delta = substitute(pEffects, call.data, call.isStatic);
                  newEffects.addAll(delta);
                }
                else {
                  callKeys.add(call.key);
                  newEffects.add(call);
                }
              }
              else {
                newEffects.add(dEffect);
              }
            }

            if (TOP_EFFECTS.equals(delta)) {
              solved.put(dKey, TOP_EFFECTS);
              moving.push(dKey);
            }
            else if (callKeys.isEmpty()) {
              solved.put(dKey, newEffects);
              moving.push(dKey);
            }
            else {
              pending.put(dKey, newEffects);
            }
          }


        }
      }

    }
    return solved;
  }

  private static Set<EffectQuantum> substitute(Set<EffectQuantum> effects, DataValue[] data, boolean isStatic) {
    if (effects.isEmpty() || TOP_EFFECTS.equals(effects)) {
      return effects;
    }
    Set<EffectQuantum> newEffects = new HashSet<>(effects.size());
    int shift = isStatic ? 0 : 1;
    for (EffectQuantum effect : effects) {
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
      return TOP_EFFECTS;
    }
    return newEffects;
  }
}