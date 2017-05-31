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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis.LOG;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisIndex extends ScalarIndexExtension<HMethod> {
  private static final ID<HMethod, Void> NAME = ID.create("bytecodeAnalysis");
  private static final HKeyDescriptor KEY_DESCRIPTOR = new HKeyDescriptor();

  private static final int VERSION = 4; // change when inference algorithm changes
  private static final int VERSION_MODIFIER = HardCodedPurity.AGGRESSIVE_HARDCODED_PURITY ? 1 : 0;
  private static final int VERSION_MODIFIER_MAX = 2;
  private static final int FINAL_VERSION = VERSION * VERSION_MODIFIER_MAX + VERSION_MODIFIER;

  private static final VirtualFileGist<Map<HMethod, Equations>> ourGist = GistManager.getInstance().newVirtualFileGist(
    "BytecodeAnalysisIndex", FINAL_VERSION, new EquationsExternalizer(), new ClassDataIndexer());

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
  private static Map<HMethod, Void> collectKeys(byte[] content) throws NoSuchAlgorithmException {
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
      return DataInputOutputUtilRt.readSeq(in, () -> Pair.create(KEY_DESCRIPTOR.read(in), readEquations(in))).
        stream().collect(Collectors.toMap(p -> p.getFirst(), p -> p.getSecond()));
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
              out.write(hKey.method.hashed(md).myBytes);
              int rawDirKey = hKey.negated ? -hKey.dirKey : hKey.dirKey;
              DataInputOutputUtil.writeINT(out, rawDirKey);
              out.writeBoolean(hKey.stable);
            }
          }
        }
        else if (rhs instanceof Effects) {
          Effects effects = (Effects)rhs;
          DataInputOutputUtil.writeINT(out, effects.effects.size());
          for (EffectQuantum effect : effects.effects) {
            if (effect == EffectQuantum.TopEffectQuantum) {
              DataInputOutputUtil.writeINT(out, -1);
            }
            else if (effect == EffectQuantum.ThisChangeQuantum) {
              DataInputOutputUtil.writeINT(out, -2);
            }
            else if (effect instanceof EffectQuantum.CallQuantum) {
              DataInputOutputUtil.writeINT(out, -3);
              EffectQuantum.CallQuantum callQuantum = (EffectQuantum.CallQuantum)effect;
              out.write(callQuantum.key.method.hashed(md).myBytes);
              DataInputOutputUtil.writeINT(out, callQuantum.key.dirKey);
              out.writeBoolean(callQuantum.key.stable);
              out.writeBoolean(callQuantum.isStatic);
              DataInputOutputUtil.writeINT(out, callQuantum.data.length);
              for (DataValue dataValue : callQuantum.data) {
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
                else if (dataValue instanceof DataValue.ParameterDataValue) {
                  DataInputOutputUtil.writeINT(out, ((DataValue.ParameterDataValue)dataValue).n);
                }
              }
            }
            else if (effect instanceof EffectQuantum.ParamChangeQuantum) {
              DataInputOutputUtil.writeINT(out, ((EffectQuantum.ParamChangeQuantum)effect).n);
            }
          }
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
            int effectMask = DataInputOutputUtil.readINT(in);
            if (effectMask == -1) {
              effects.add(EffectQuantum.TopEffectQuantum);
            }
            else if (effectMask == -2) {
              effects.add(EffectQuantum.ThisChangeQuantum);
            }
            else if (effectMask == -3){
              byte[] bytes = new byte[HMethod.HASH_SIZE];
              in.readFully(bytes);
              int rawDirKey = DataInputOutputUtil.readINT(in);
              boolean isStable = in.readBoolean();
              EKey key = new EKey(new HMethod(bytes), Math.abs(rawDirKey), isStable, false);
              boolean isStatic = in.readBoolean();
              int dataLength = DataInputOutputUtil.readINT(in);
              DataValue[] data = new DataValue[dataLength];
              for (int di = 0; di < dataLength; di++) {
                int dataI = DataInputOutputUtil.readINT(in);
                if (dataI == -1) {
                  data[di] = DataValue.ThisDataValue;
                }
                else if (dataI == -2) {
                  data[di] = DataValue.LocalDataValue;
                }
                else if (dataI == -3) {
                  data[di] = DataValue.OwnedDataValue;
                }
                else if (dataI == -4) {
                  data[di] = DataValue.UnknownDataValue1;
                }
                else if (dataI == -5) {
                  data[di] = DataValue.UnknownDataValue2;
                }
                else {
                  data[di] = new DataValue.ParameterDataValue(dataI);
                }
              }
              effects.add(new EffectQuantum.CallQuantum(key, data, isStatic));
            }
            else {
              effects.add(new EffectQuantum.ParamChangeQuantum(effectMask));
            }
          }
          results.add(new DirectionResultPair(directionKey, new Effects(effects)));
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
                byte[] bytes = new byte[HMethod.HASH_SIZE];
                in.readFully(bytes);
                int rawDirKey = DataInputOutputUtil.readINT(in);
                ids[j] = new EKey(new HMethod(bytes), Direction.fromInt(Math.abs(rawDirKey)), in.readBoolean(), rawDirKey < 0);
              }
              components[i] = new Component(value, ids);
            }
            results.add(new DirectionResultPair(directionKey, new Pending(components)));
          }
        }
      }
      return new Equations(results, stable);
    }
  }
}
