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
import com.intellij.openapi.util.Couple;
import org.jetbrains.annotations.NotNull;
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
  static final Set<EffectQuantum> topEffect = Collections.singleton(EffectQuantum.TopEffectQuantum);
  static final Set<HEffectQuantum> topHEffect = Collections.singleton(HEffectQuantum.TopEffectQuantum);

  static final int UN_ANALYZABLE_FLAG = Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_INTERFACE;

  @NotNull
  public static Equation analyze(Method method, MethodNode methodNode, boolean stable) {
    Key key = new Key(method, Direction.Pure, stable);
    Set<EffectQuantum> hardCodedSolution = HardCodedPurity.getHardCodedSolution(key);
    if (hardCodedSolution != null) {
      return new Equation(key, new Effects(hardCodedSolution));
    }

    if ((methodNode.access & UN_ANALYZABLE_FLAG) != 0) {
      return new Equation(key, new Effects(topEffect));
    }

    DataInterpreter dataInterpreter = new DataInterpreter(methodNode);
    try {
      new Analyzer<>(dataInterpreter).analyze("this", methodNode);
    }
    catch (AnalyzerException e) {
      return new Equation(key, new Effects(topEffect));
    }
    EffectQuantum[] quanta = dataInterpreter.effects;
    Set<EffectQuantum> effects = new HashSet<>();
    for (EffectQuantum effectQuantum : quanta) {
      if (effectQuantum != null) {
        if (effectQuantum == EffectQuantum.TopEffectQuantum) {
          return new Equation(key, new Effects(topEffect));
        }
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
  };
  static final DataValue LocalDataValue = new DataValue(-2) {
    @Override
    public int getSize() {
      return 1;
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

  }
  static final DataValue OwnedDataValue = new DataValue(-3) {
    @Override
    public int getSize() {
      return 1;
    }
  };
  static final DataValue UnknownDataValue1 = new DataValue(-4) {
    @Override
    public int getSize() {
      return 1;
    }
  };
  static final DataValue UnknownDataValue2 = new DataValue(-5) {
    @Override
    public int getSize() {
      return 2;
    }
  };
}

interface EffectQuantum {
  EffectQuantum TopEffectQuantum = new EffectQuantum() {};
  EffectQuantum ThisChangeQuantum = new EffectQuantum() {};
  class ParamChangeQuantum implements EffectQuantum {
    final int n;
    public ParamChangeQuantum(int n) {
      this.n = n;
    }
  }
  class CallQuantum implements EffectQuantum {
    final Key key;
    final DataValue[] data;
    final boolean isStatic;
    public CallQuantum(Key key, DataValue[] data, boolean isStatic) {
      this.key = key;
      this.data = data;
      this.isStatic = isStatic;
    }
  }
}

abstract class HEffectQuantum {
  private final int myHash;

  HEffectQuantum(int hash) {
    myHash = hash;
  }

  @Override
  public final int hashCode() {
    return myHash;
  }

  static final HEffectQuantum TopEffectQuantum = new HEffectQuantum(-1) {};
  static final HEffectQuantum ThisChangeQuantum = new HEffectQuantum(-2) {};
  static class ParamChangeQuantum extends HEffectQuantum {
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
  }
  static class CallQuantum extends HEffectQuantum {
    final HKey key;
    final DataValue[] data;
    final boolean isStatic;
    public CallQuantum(HKey key, DataValue[] data, boolean isStatic) {
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
        effects[insnIndex] = EffectQuantum.TopEffectQuantum;
        return (ASMUtils.getReturnSizeFast(((InvokeDynamicInsnNode)insn).desc) == 1) ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
      case Opcodes.INVOKEVIRTUAL:
      case Opcodes.INVOKESPECIAL:
      case Opcodes.INVOKESTATIC:
      case Opcodes.INVOKEINTERFACE:
        boolean stable = opCode == Opcodes.INVOKESPECIAL || opCode == Opcodes.INVOKESTATIC;
        MethodInsnNode mNode = ((MethodInsnNode)insn);
        DataValue[] data = values.toArray(new DataValue[values.size()]);
        Key key = new Key(new Method(mNode.owner, mNode.name, mNode.desc), Direction.Pure, stable);
        effects[insnIndex] = new EffectQuantum.CallQuantum(key, data, opCode == Opcodes.INVOKESTATIC);
        return (ASMUtils.getReturnSizeFast(mNode.desc) == 1) ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
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
        if (value == DataValue.ThisDataValue && HardCodedPurity.ownedFields.contains(new Couple<>(fieldInsn.owner, fieldInsn.name))) {
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

final class HardCodedPurity {
  static Set<Couple<String>> ownedFields = new HashSet<>();
  static Map<Method, Set<EffectQuantum>> solutions = new HashMap<>();
  static Set<EffectQuantum> thisChange = Collections.singleton(EffectQuantum.ThisChangeQuantum);
  static {
    ownedFields.add(new Couple<>("java/lang/AbstractStringBuilder", "value"));

    solutions.put(new Method("java/lang/Throwable", "fillInStackTrace", "(I)Ljava/lang/Throwable;"), thisChange);
    solutions.put(new Method("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V"), Collections.<EffectQuantum>singleton(new EffectQuantum.ParamChangeQuantum(2)));
    solutions.put(new Method("java/lang/AbstractStringBuilder", "expandCapacity", "(I)V"), thisChange);
    solutions.put(new Method("java/lang/StringBuilder", "expandCapacity", "(I)V"), thisChange);
    solutions.put(new Method("java/lang/StringBuffer", "expandCapacity", "(I)V"), thisChange);
    solutions.put(new Method("java/lang/StringIndexOutOfBoundsException", "<init>", "(I)V"), thisChange);
  }

  static Set<EffectQuantum> getHardCodedSolution(Key key) {
    Method method = key.method;
    if (method.methodName.equals("fillInStackTrace") && method.methodDesc.equals("()Ljava/lang/Throwable;")) {
      return thisChange;
    }
    return solutions.get(key.method);
  }

  static Set<EffectQuantum> getHardCodedSolution(HKey key) {
    // TODO: implement the logic as in https://github.com/ilya-klyuchnikov/faba/blob/2ffab410416e0a9f8e35d5071df50bcf27b1e149/src/main/scala/asm/purity.scala#L238
    // The problem with porting logic from Scala version "as is" is that in Scala version original keys (Key) are used.
    // Here (in IDEA) the hashed keys (HKey) are used. In a general hashed keys may lead to collisions.
    // So in order to port the logic, hardcoded solutions should be used with stable keys,
    // that is - during analysis - com.intellij.codeInspection.bytecodeAnalysis.DataInterpreter.naryOperation
    return null;
  }
}

final class PuritySolver {
  private HashMap<HKey, Set<HEffectQuantum>> solved = new HashMap<>();
  private HashMap<HKey, Set<HKey>> dependencies = new HashMap<>();
  private final Stack<HKey> moving = new Stack<>();
  private HashMap<HKey, Set<HEffectQuantum>> pending = new HashMap<>();

  void addEquation(HKey key, Set<HEffectQuantum> effects) {
    Set<HKey> callKeys = new HashSet<>();
    for (HEffectQuantum effect : effects) {
      if (effect instanceof HEffectQuantum.CallQuantum) {
        callKeys.add(((HEffectQuantum.CallQuantum)effect).key);
      }
    }

    if (callKeys.isEmpty()) {
      solved.put(key, effects);
      moving.add(key);
    } else {
      pending.put(key, effects);
      for (HKey callKey : callKeys) {
        Set<HKey> deps = dependencies.get(callKey);
        if (deps == null) {
          deps = new HashSet<>();
          dependencies.put(callKey, deps);
        }
        deps.add(key);
      }
    }
  }

  public Map<HKey, Set<HEffectQuantum>> solve() {
    while (!moving.isEmpty()) {
      HKey key = moving.pop();
      Set<HEffectQuantum> effects = solved.get(key);

      HKey[] propagateKeys;
      Set[] propagateEffects;

      if (key.stable) {
        propagateKeys = new HKey[]{key, key.mkUnstable()};
        propagateEffects = new Set[]{effects, effects};
      }
      else {
        propagateKeys = new HKey[]{key.mkStable(), key};
        propagateEffects = new Set[]{effects, mkUnstableEffects(key)};
      }
      for (int i = 0; i < propagateKeys.length; i++) {
        HKey pKey = propagateKeys[i];
        Set<HEffectQuantum> pEffects = propagateEffects[i];
        Set<HKey> dKeys = dependencies.remove(pKey);
        if (dKeys != null) {
          for (HKey dKey : dKeys) {
            Set<HEffectQuantum> dEffects = pending.remove(dKey);
            if (dEffects == null) {
              // already solved, for example, solution is top
              continue;
            }
            Set<HKey> callKeys = new HashSet<>();
            Set<HEffectQuantum> newEffects = new HashSet<>();
            Set<HEffectQuantum> delta = null;

            for (HEffectQuantum dEffect : dEffects) {
              if (dEffect instanceof HEffectQuantum.CallQuantum) {
                HEffectQuantum.CallQuantum call = ((HEffectQuantum.CallQuantum)dEffect);
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

            if (PurityAnalysis.topHEffect.equals(delta)) {
              solved.put(dKey, PurityAnalysis.topHEffect);
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

  private Set<HEffectQuantum> substitute(Set<HEffectQuantum> effects, DataValue[] data, boolean isStatic) {
    if (effects.isEmpty() || PurityAnalysis.topHEffect.equals(effects)) {
      return effects;
    }
    else {
      Set<HEffectQuantum> newEffects = new HashSet<>();
      int shift = isStatic ? 0 : 1;
      for (HEffectQuantum effect : effects) {
        if (effect == HEffectQuantum.ThisChangeQuantum) {
          DataValue thisArg = data[0];
          if (thisArg == DataValue.ThisDataValue || thisArg == DataValue.OwnedDataValue) {
            newEffects.add(HEffectQuantum.ThisChangeQuantum);
          }
          else if (thisArg == DataValue.LocalDataValue) {
            // nothing
          }
          else if (thisArg instanceof DataValue.ParameterDataValue) {
            newEffects.add(new HEffectQuantum.ParamChangeQuantum(((DataValue.ParameterDataValue)thisArg).n));
          }
          else {
            return PurityAnalysis.topHEffect;
          }
        }
        else if (effect instanceof HEffectQuantum.ParamChangeQuantum) {
          HEffectQuantum.ParamChangeQuantum paramChange = ((HEffectQuantum.ParamChangeQuantum)effect);
          DataValue paramArg = data[paramChange.n + shift];
          if (paramArg == DataValue.ThisDataValue || paramArg == DataValue.OwnedDataValue) {
            newEffects.add(HEffectQuantum.ThisChangeQuantum);
          }
          else if (paramArg == DataValue.LocalDataValue) {
            // nothing
          }
          else if (paramArg instanceof DataValue.ParameterDataValue) {
            newEffects.add(new HEffectQuantum.ParamChangeQuantum(((DataValue.ParameterDataValue)paramArg).n));
          }
          else {
            return PurityAnalysis.topHEffect;
          }
        }
      }
      return newEffects;
    }
  }

  private static Set mkUnstableEffects(HKey key) {
    Set<EffectQuantum> hardcodedEffects = HardCodedPurity.getHardCodedSolution(key);
    return hardcodedEffects == null ? PurityAnalysis.topHEffect : hardcodedEffects;
  }
}