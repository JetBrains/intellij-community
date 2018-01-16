/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.VirtualFileGist;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DifferentSerializableBytesImplyNonEqualityPolicy;
import com.intellij.util.io.KeyDescriptor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BinaryOperator;

import static com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis.LOG;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisIndex extends ScalarIndexExtension<HMethod> {
  private static final ID<HMethod, Void> NAME = ID.create("bytecodeAnalysis");
  private static final HKeyDescriptor KEY_DESCRIPTOR = new HKeyDescriptor();

  private static final int VERSION = 8; // change when inference algorithm changes
  private static final int VERSION_MODIFIER = HardCodedPurity.AGGRESSIVE_HARDCODED_PURITY ? 1 : 0;
  private static final int FINAL_VERSION = VERSION * 2 + VERSION_MODIFIER;

  private static final VirtualFileGist<Map<HMethod, Equations>> ourGist = GistManager.getInstance().newVirtualFileGist(
    "BytecodeAnalysisIndex", FINAL_VERSION, new EquationsExternalizer(), new ClassDataIndexer());
  // Hash collision is possible: resolve it just flushing all the equations for colliding methods (unless equations are the same)
  static final BinaryOperator<Equations> MERGER =
    (eq1, eq2) -> eq1.equals(eq2) ? eq1 : new Equations(Collections.emptyList(), false);

  @NotNull
  @Override
  public ID<HMethod, Void> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<HMethod, Void, FileContent> getIndexer() {
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
  private static Map<HMethod, Void> collectKeys(byte[] content) {
    HashMap<HMethod, Void> map = new HashMap<>();
    MessageDigest md = BytecodeAnalysisConverter.getMessageDigest();
    new ClassReader(content).accept(new KeyedMethodVisitor() {
      @Nullable
      @Override
      MethodVisitor visitMethod(MethodNode node, Method method, EKey key) {
        map.put(method.hashed(md), null);
        return null;
      }
    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return map;
  }

  @NotNull
  @Override
  public KeyDescriptor<HMethod> getKeyDescriptor() {
    return KEY_DESCRIPTOR;
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(JavaClassFileType.INSTANCE);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 10;
  }

  @NotNull
  static List<Equations> getEquations(GlobalSearchScope scope, HMethod key) {
    Project project = ProjectManager.getInstance().getDefaultProject(); // the data is project-independent
    return ContainerUtil.mapNotNull(FileBasedIndex.getInstance().getContainingFiles(NAME, key, scope),
                                    file -> ourGist.getFileData(project, file).get(key));
  }

  /**
   * Externalizer for primary method keys.
   */
  private static class HKeyDescriptor implements KeyDescriptor<HMethod>, DifferentSerializableBytesImplyNonEqualityPolicy {

    @Override
    public void save(@NotNull DataOutput out, HMethod value) throws IOException {
      out.write(value.myBytes);
    }

    @Override
    public HMethod read(@NotNull DataInput in) throws IOException {
      byte[] bytes = new byte[HMethod.HASH_SIZE];
      in.readFully(bytes);
      return new HMethod(bytes);
    }

    @Override
    public int getHashCode(HMethod value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(HMethod val1, HMethod val2) {
      return val1.equals(val2);
    }
  }

  /**
   * Externalizer for compressed equations.
   */
  public static class EquationsExternalizer implements DataExternalizer<Map<HMethod, Equations>> {
    @Override
    public void save(@NotNull DataOutput out, Map<HMethod, Equations> value) throws IOException {
      DataInputOutputUtilRt.writeSeq(out, value.entrySet(), entry -> {
        KEY_DESCRIPTOR.save(out, entry.getKey());
        saveEquations(out, entry.getValue());
      });
    }

    @Override
    public Map<HMethod, Equations> read(@NotNull DataInput in) throws IOException {
      return StreamEx.of(DataInputOutputUtilRt.readSeq(in, () -> Pair.create(KEY_DESCRIPTOR.read(in), readEquations(in)))).
        toMap(p -> p.getFirst(), p -> p.getSecond(), MERGER);
    }

    private static void saveEquations(@NotNull DataOutput out, Equations eqs) throws IOException {
      out.writeBoolean(eqs.stable);
      MessageDigest md = BytecodeAnalysisConverter.getMessageDigest();
      DataInputOutputUtil.writeINT(out, eqs.results.size());
      for (DirectionResultPair pair : eqs.results) {
        DataInputOutputUtil.writeINT(out, pair.directionKey);
        Result rhs = pair.result;
        if (rhs instanceof Final) {
          Final finalResult = (Final)rhs;
          out.writeBoolean(true); // final flag
          DataInputOutputUtil.writeINT(out, finalResult.value.ordinal());
        }
        else if (rhs instanceof Pending) {
          Pending pendResult = (Pending)rhs;
          out.writeBoolean(false); // pending flag
          DataInputOutputUtil.writeINT(out, pendResult.delta.length);

          for (Component component : pendResult.delta) {
            DataInputOutputUtil.writeINT(out, component.value.ordinal());
            EKey[] ids = component.ids;
            DataInputOutputUtil.writeINT(out, ids.length);
            for (EKey hKey : ids) {
              writeKey(out, hKey, md);
            }
          }
        }
        else if (rhs instanceof Effects) {
          Effects effects = (Effects)rhs;
          DataInputOutputUtil.writeINT(out, effects.effects.size());
          for (EffectQuantum effect : effects.effects) {
            writeEffect(out, effect, md);
          }
          writeDataValue(out, effects.returnValue, md);
        }
      }
    }

    private static Equations readEquations(@NotNull DataInput in) throws IOException {
      boolean stable = in.readBoolean();
      int size = DataInputOutputUtil.readINT(in);
      ArrayList<DirectionResultPair> results = new ArrayList<>(size);
      for (int k = 0; k < size; k++) {
        int directionKey = DataInputOutputUtil.readINT(in);
        Direction direction = Direction.fromInt(directionKey);
        if (direction == Direction.Pure) {
          Set<EffectQuantum> effects = new HashSet<>();
          int effectsSize = DataInputOutputUtil.readINT(in);
          for (int i = 0; i < effectsSize; i++) {
            effects.add(readEffect(in));
          }
          DataValue returnValue = readDataValue(in);
          results.add(new DirectionResultPair(directionKey, new Effects(returnValue, effects)));
        }
        else {
          boolean isFinal = in.readBoolean(); // flag
          if (isFinal) {
            int ordinal = DataInputOutputUtil.readINT(in);
            Value value = Value.values()[ordinal];
            results.add(new DirectionResultPair(directionKey, new Final(value)));
          }
          else {
            int sumLength = DataInputOutputUtil.readINT(in);
            Component[] components = new Component[sumLength];

            for (int i = 0; i < sumLength; i++) {
              int ordinal = DataInputOutputUtil.readINT(in);
              Value value = Value.values()[ordinal];
              int componentSize = DataInputOutputUtil.readINT(in);
              EKey[] ids = new EKey[componentSize];
              for (int j = 0; j < componentSize; j++) {
                ids[j] = readKey(in);
              }
              components[i] = new Component(value, ids);
            }
            results.add(new DirectionResultPair(directionKey, new Pending(components)));
          }
        }
      }
      return new Equations(results, stable);
    }

    @NotNull
    private static EKey readKey(@NotNull DataInput in) throws IOException {
      byte[] bytes = new byte[HMethod.HASH_SIZE];
      in.readFully(bytes);
      int rawDirKey = DataInputOutputUtil.readINT(in);
      return new EKey(new HMethod(bytes), Direction.fromInt(Math.abs(rawDirKey)), in.readBoolean(), rawDirKey < 0);
    }

    private static void writeKey(@NotNull DataOutput out, EKey key, MessageDigest md) throws IOException {
      out.write(key.method.hashed(md).myBytes);
      int rawDirKey = key.negated ? -key.dirKey : key.dirKey;
      DataInputOutputUtil.writeINT(out, rawDirKey);
      out.writeBoolean(key.stable);
    }

    private static void writeEffect(@NotNull DataOutput out, EffectQuantum effect, MessageDigest md) throws IOException {
      if (effect == EffectQuantum.TopEffectQuantum) {
        DataInputOutputUtil.writeINT(out, -1);
      }
      else if (effect == EffectQuantum.ThisChangeQuantum) {
        DataInputOutputUtil.writeINT(out, -2);
      }
      else if (effect instanceof EffectQuantum.CallQuantum) {
        DataInputOutputUtil.writeINT(out, -3);
        EffectQuantum.CallQuantum callQuantum = (EffectQuantum.CallQuantum)effect;
        writeKey(out, callQuantum.key, md);
        out.writeBoolean(callQuantum.isStatic);
        DataInputOutputUtil.writeINT(out, callQuantum.data.length);
        for (DataValue dataValue : callQuantum.data) {
          writeDataValue(out, dataValue, md);
        }
      }
      else if (effect instanceof EffectQuantum.ReturnChangeQuantum) {
        DataInputOutputUtil.writeINT(out, -4);
        writeKey(out, ((EffectQuantum.ReturnChangeQuantum)effect).key, md);
      }
      else if (effect instanceof EffectQuantum.ParamChangeQuantum) {
        DataInputOutputUtil.writeINT(out, ((EffectQuantum.ParamChangeQuantum)effect).n);
      }
    }

    private static EffectQuantum readEffect(@NotNull DataInput in) throws IOException {
      int effectMask = DataInputOutputUtil.readINT(in);
      switch (effectMask) {
        case -1:
          return EffectQuantum.TopEffectQuantum;
        case -2:
          return EffectQuantum.ThisChangeQuantum;
        case -3:
          EKey key = readKey(in);
          boolean isStatic = in.readBoolean();
          int dataLength = DataInputOutputUtil.readINT(in);
          DataValue[] data = new DataValue[dataLength];
          for (int di = 0; di < dataLength; di++) {
            data[di] = readDataValue(in);
          }
          return new EffectQuantum.CallQuantum(key, data, isStatic);
        case -4:
          return new EffectQuantum.ReturnChangeQuantum(readKey(in));
        default:
          return new EffectQuantum.ParamChangeQuantum(effectMask);
      }
    }

    private static void writeDataValue(@NotNull DataOutput out, DataValue dataValue, MessageDigest md) throws IOException {
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
        writeKey(out, ((DataValue.ReturnDataValue)dataValue).key, md);
      }
      else if (dataValue instanceof DataValue.ParameterDataValue) {
        DataInputOutputUtil.writeINT(out, ((DataValue.ParameterDataValue)dataValue).n);
      }
    }

    private static DataValue readDataValue(@NotNull DataInput in) throws IOException {
      int dataI = DataInputOutputUtil.readINT(in);
      switch (dataI) {
        case -1:
          return DataValue.ThisDataValue;
        case -2:
          return DataValue.LocalDataValue;
        case -3:
          return DataValue.OwnedDataValue;
        case -4:
          return DataValue.UnknownDataValue1;
        case -5:
          return DataValue.UnknownDataValue2;
        case -6:
          return new DataValue.ReturnDataValue(readKey(in));
        default:
          return new DataValue.ParameterDataValue(dataI);
      }
    }
  }
}
