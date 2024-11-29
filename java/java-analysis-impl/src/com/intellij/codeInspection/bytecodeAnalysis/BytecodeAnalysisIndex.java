// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.util.SystemProperties;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DifferentSerializableBytesImplyNonEqualityPolicy;
import com.intellij.util.io.KeyDescriptor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

import static com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis.LOG;

public final class BytecodeAnalysisIndex extends ScalarIndexExtension<HMember> {
  private static final boolean IS_ENABLED = SystemProperties.getBooleanProperty("bytecodeAnalysis.index.enabled", true);
  static final ID<HMember, Void> NAME = ID.create("bytecodeAnalysis");

  @NotNull
  @Override
  public ID<HMember, Void> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<HMember, Void, FileContent> getIndexer() {
    return inputData -> {
      try {
        return collectKeys(inputData.getContent());
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        // incorrect bytecode may result in Runtime exceptions during analysis
        // so here we suppose that exception is due to incorrect bytecode
        LOG.debug("Unexpected Error during indexing of bytecode", e);
        return Collections.emptyMap();
      }
    };
  }

  @NotNull
  private static Map<HMember, Void> collectKeys(byte[] content) {
    HashMap<HMember, Void> map = new HashMap<>();
    ClassReader reader = new ClassReader(content);
    String className = reader.getClassName();
    long classHash = HMember.classHash(className);
    reader.accept(new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        if ((access & Opcodes.ACC_PRIVATE) == 0) {
          map.put(HMember.create(classHash, name, desc), null);
        }
        return null;
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        map.put(HMember.create(classHash, name, desc), null);
        return null;
      }
    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);
    return map;
  }

  @NotNull
  @Override
  public KeyDescriptor<HMember> getKeyDescriptor() {
    return HKeyDescriptor.INSTANCE;
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return IS_ENABLED
           ? new DefaultFileTypeSpecificInputFilter(JavaClassFileType.INSTANCE)
           : new DefaultFileTypeSpecificInputFilter();
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 11;
  }

  @Override
  public boolean needsForwardIndexWhenSharing() {
    return false;
  }

  /**
   * Externalizer for primary method keys.
   */
  private static class HKeyDescriptor implements KeyDescriptor<HMember>, DifferentSerializableBytesImplyNonEqualityPolicy {
    static final HKeyDescriptor INSTANCE = new HKeyDescriptor();

    @Override
    public void save(@NotNull DataOutput out, HMember value) throws IOException {
      out.write(value.asBytes());
    }

    @Override
    public HMember read(@NotNull DataInput in) throws IOException {
      byte[] bytes = new byte[HMember.HASH_SIZE];
      in.readFully(bytes);
      return new HMember(bytes);
    }

    @Override
    public int getHashCode(HMember value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(HMember val1, HMember val2) {
      return val1.equals(val2);
    }
  }

  /**
   * Externalizer for compressed equations.
   */
  public static class EquationsExternalizer implements DataExternalizer<Map<HMember, Equations>> {
    @Override
    public void save(@NotNull DataOutput out, Map<HMember, Equations> value) throws IOException {
      DataInputOutputUtilRt.writeSeq(out, value.entrySet(), entry -> {
        HKeyDescriptor.INSTANCE.save(out, entry.getKey());
        saveEquations(out, entry.getValue());
      });
    }

    @Override
    public Map<HMember, Equations> read(@NotNull DataInput in) throws IOException {
      return StreamEx.of(DataInputOutputUtilRt.readSeq(in, () -> Pair.create(HKeyDescriptor.INSTANCE.read(in), readEquations(in)))).
        toMap(p -> p.getFirst(), p -> p.getSecond(), ClassDataIndexer.MERGER);
    }

    private static void saveEquations(@NotNull DataOutput out, Equations eqs) throws IOException {
      out.writeBoolean(eqs.stable);
      DataInputOutputUtil.writeINT(out, eqs.results.size());
      int maxFinal = Value.values().length;
      for (DirectionResultPair pair : eqs.results) {
        DataInputOutputUtil.writeINT(out, pair.directionKey);
        Result rhs = pair.result;
        if (rhs instanceof Value finalResult) {
          DataInputOutputUtil.writeINT(out, finalResult.ordinal());
        }
        else if (rhs instanceof Pending pendResult) {
          DataInputOutputUtil.writeINT(out, maxFinal); // pending flag
          DataInputOutputUtil.writeINT(out, pendResult.delta.length);

          for (Component component : pendResult.delta) {
            DataInputOutputUtil.writeINT(out, component.value.ordinal());
            EKey[] ids = component.ids;
            DataInputOutputUtil.writeINT(out, ids.length);
            for (EKey hKey : ids) {
              writeKey(out, hKey);
            }
          }
        }
        else if (rhs instanceof Effects effects) {
          DataInputOutputUtil.writeINT(out, effects.effects.size());
          for (EffectQuantum effect : effects.effects) {
            writeEffect(out, effect);
          }
          writeDataValue(out, effects.returnValue);
        }
        else if (rhs instanceof FieldAccess fieldAccess) {
          DataInputOutputUtil.writeINT(out, maxFinal + 1);
          out.writeUTF(fieldAccess.name());
        }
        else {
          throw new UnsupportedOperationException("Unsupported result: " + rhs + " in " + eqs);
        }
      }
    }

    private static Equations readEquations(@NotNull DataInput in) throws IOException {
      boolean stable = in.readBoolean();
      int size = DataInputOutputUtil.readINT(in);
      ArrayList<DirectionResultPair> results = new ArrayList<>(size);
      Value[] values = Value.values();
      for (int k = 0; k < size; k++) {
        int directionKey = DataInputOutputUtil.readINT(in);
        Direction direction = Direction.fromInt(directionKey);
        if (direction == Direction.Pure || direction == Direction.Volatile) {
          List<EffectQuantum> effects = new ArrayList<>();
          int effectsSize = DataInputOutputUtil.readINT(in);
          for (int i = 0; i < effectsSize; i++) {
            effects.add(readEffect(in));
          }
          DataValue returnValue = readDataValue(in);
          results.add(new DirectionResultPair(directionKey, new Effects(returnValue, Set.copyOf(effects))));
        }
        else {
          int resultKind = DataInputOutputUtil.readINT(in);
          if (resultKind == values.length) {
            // pending
            int sumLength = DataInputOutputUtil.readINT(in);
            Component[] components = new Component[sumLength];

            for (int i = 0; i < sumLength; i++) {
              int ordinal = DataInputOutputUtil.readINT(in);
              Value value = values[ordinal];
              int componentSize = DataInputOutputUtil.readINT(in);
              EKey[] ids = new EKey[componentSize];
              for (int j = 0; j < componentSize; j++) {
                ids[j] = readKey(in);
              }
              components[i] = new Component(value, ids);
            }
            results.add(new DirectionResultPair(directionKey, new Pending(components)));
          }
          else if (resultKind == values.length + 1) {
            results.add(new DirectionResultPair(directionKey, new FieldAccess(in.readUTF())));
          }
          else {
            Value value = values[resultKind];
            results.add(new DirectionResultPair(directionKey, value));
          }
        }
      }
      return new Equations(results, stable);
    }

    @NotNull
    private static EKey readKey(@NotNull DataInput in) throws IOException {
      byte[] bytes = new byte[HMember.HASH_SIZE];
      in.readFully(bytes);
      int rawDirKey = DataInputOutputUtil.readINT(in);
      return new EKey(new HMember(bytes), Direction.fromInt(Math.abs(rawDirKey)), in.readBoolean(), rawDirKey < 0);
    }

    private static void writeKey(@NotNull DataOutput out, EKey key) throws IOException {
      out.write(key.member.hashed().asBytes());
      int rawDirKey = key.negated ? -key.dirKey : key.dirKey;
      DataInputOutputUtil.writeINT(out, rawDirKey);
      out.writeBoolean(key.stable);
    }

    private static void writeEffect(@NotNull DataOutput out, EffectQuantum effect) throws IOException {
      if (effect == EffectQuantum.TopEffectQuantum) {
        DataInputOutputUtil.writeINT(out, -1);
      }
      else if (effect == EffectQuantum.ThisChangeQuantum) {
        DataInputOutputUtil.writeINT(out, -2);
      }
      else if (effect instanceof EffectQuantum.CallQuantum callQuantum) {
        DataInputOutputUtil.writeINT(out, -3);
        writeKey(out, callQuantum.key);
        out.writeBoolean(callQuantum.isStatic);
        DataInputOutputUtil.writeINT(out, callQuantum.data.length);
        for (DataValue dataValue : callQuantum.data) {
          writeDataValue(out, dataValue);
        }
      }
      else if (effect instanceof EffectQuantum.ReturnChangeQuantum returnChangeQuantum) {
        DataInputOutputUtil.writeINT(out, -4);
        writeKey(out, returnChangeQuantum.key);
      }
      else if (effect instanceof EffectQuantum.FieldReadQuantum fieldReadQuantum) {
        DataInputOutputUtil.writeINT(out, -5);
        writeKey(out, fieldReadQuantum.key);
      }
      else if (effect instanceof EffectQuantum.ParamChangeQuantum paramChangeQuantum) {
        DataInputOutputUtil.writeINT(out, paramChangeQuantum.n);
      }
    }

    private static EffectQuantum readEffect(@NotNull DataInput in) throws IOException {
      int effectMask = DataInputOutputUtil.readINT(in);
      return switch (effectMask) {
        case -1 -> EffectQuantum.TopEffectQuantum;
        case -2 -> EffectQuantum.ThisChangeQuantum;
        case -3 -> {
          EKey key = readKey(in);
          boolean isStatic = in.readBoolean();
          int dataLength = DataInputOutputUtil.readINT(in);
          DataValue[] data = new DataValue[dataLength];
          for (int di = 0; di < dataLength; di++) {
            data[di] = readDataValue(in);
          }
          yield new EffectQuantum.CallQuantum(key, data, isStatic);
        }
        case -4 -> new EffectQuantum.ReturnChangeQuantum(readKey(in));
        case -5 -> new EffectQuantum.FieldReadQuantum(readKey(in));
        default -> new EffectQuantum.ParamChangeQuantum(effectMask);
      };
    }

    private static void writeDataValue(@NotNull DataOutput out, DataValue dataValue) throws IOException {
      if (dataValue == DataValue.ThisDataValue) {
        DataInputOutputUtil.writeINT(out, -1);
      }
      else if (dataValue == DataValue.LocalDataValue) {
        DataInputOutputUtil.writeINT(out, -2);
      }
      else if (dataValue == DataValue.OwnedDataValue) {
        DataInputOutputUtil.writeINT(out, -3);
      }
      else if (dataValue == DataValue.UnknownDataValue1) {
        DataInputOutputUtil.writeINT(out, -4);
      }
      else if (dataValue == DataValue.UnknownDataValue2) {
        DataInputOutputUtil.writeINT(out, -5);
      }
      else if (dataValue instanceof DataValue.ReturnDataValue) {
        DataInputOutputUtil.writeINT(out, -6);
        writeKey(out, ((DataValue.ReturnDataValue)dataValue).key);
      }
      else if (dataValue instanceof DataValue.ParameterDataValue) {
        DataInputOutputUtil.writeINT(out, ((DataValue.ParameterDataValue)dataValue).n);
      }
    }

    private static DataValue readDataValue(@NotNull DataInput in) throws IOException {
      int dataI = DataInputOutputUtil.readINT(in);
      return switch (dataI) {
        case -1 -> DataValue.ThisDataValue;
        case -2 -> DataValue.LocalDataValue;
        case -3 -> DataValue.OwnedDataValue;
        case -4 -> DataValue.UnknownDataValue1;
        case -5 -> DataValue.UnknownDataValue2;
        case -6 -> new DataValue.ReturnDataValue(readKey(in));
        default -> DataValue.ParameterDataValue.create(dataI);
      };
    }
  }
}