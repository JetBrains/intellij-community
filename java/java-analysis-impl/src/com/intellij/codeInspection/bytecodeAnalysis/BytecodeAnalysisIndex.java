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
public class BytecodeAnalysisIndex extends ScalarIndexExtension<Bytes> {
  private static final ID<Bytes, Void> NAME = ID.create("bytecodeAnalysis");
  private static final HKeyDescriptor KEY_DESCRIPTOR = new HKeyDescriptor();
  private static final VirtualFileGist<Map<Bytes, HEquations>> ourGist = GistManager.getInstance().newVirtualFileGist(
    "BytecodeAnalysisIndex", 4, new HEquationsExternalizer(), new ClassDataIndexer());

  @NotNull
  @Override
  public ID<Bytes, Void> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<Bytes, Void, FileContent> getIndexer() {
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
  private static Map<Bytes, Void> collectKeys(byte[] content) throws NoSuchAlgorithmException {
    HashMap<Bytes, Void> map = new HashMap<>();
    MessageDigest md = BytecodeAnalysisConverter.getMessageDigest();
    new ClassReader(content).accept(new KeyedMethodVisitor() {
      @Nullable
      @Override
      MethodVisitor visitMethod(MethodNode node, Key key) {
        map.put(ClassDataIndexer.compressKey(md, key), null);
        return null;
      }
    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return map;
  }

  @NotNull
  @Override
  public KeyDescriptor<Bytes> getKeyDescriptor() {
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
  static List<HEquations> getEquations(GlobalSearchScope scope, Bytes key) {
    Project project = ProjectManager.getInstance().getDefaultProject(); // the data is project-independent
    return ContainerUtil.mapNotNull(FileBasedIndex.getInstance().getContainingFiles(NAME, key, scope),
                                    file -> ourGist.getFileData(project, file).get(key));
  }

  /**
   * Externalizer for primary method keys.
   */
  private static class HKeyDescriptor implements KeyDescriptor<Bytes>, DifferentSerializableBytesImplyNonEqualityPolicy {

    @Override
    public void save(@NotNull DataOutput out, Bytes value) throws IOException {
      out.write(value.bytes);
    }

    @Override
    public Bytes read(@NotNull DataInput in) throws IOException {
      byte[] bytes = new byte[BytecodeAnalysisConverter.HASH_SIZE];
      in.readFully(bytes);
      return new Bytes(bytes);
    }

    @Override
    public int getHashCode(Bytes value) {
      return Arrays.hashCode(value.bytes);
    }

    @Override
    public boolean isEqual(Bytes val1, Bytes val2) {
      return Arrays.equals(val1.bytes, val2.bytes);
    }
  }

  /**
   * Externalizer for compressed equations.
   */
  public static class HEquationsExternalizer implements DataExternalizer<Map<Bytes, HEquations>> {
    @Override
    public void save(@NotNull DataOutput out, Map<Bytes, HEquations> value) throws IOException {
      DataInputOutputUtilRt.writeSeq(out, value.entrySet(), entry -> {
        KEY_DESCRIPTOR.save(out, entry.getKey());
        saveEquations(out, entry.getValue());
      });
    }

    @Override
    public Map<Bytes, HEquations> read(@NotNull DataInput in) throws IOException {
      return DataInputOutputUtilRt.readSeq(in, () -> Pair.create(KEY_DESCRIPTOR.read(in), readEquations(in))).
        stream().collect(Collectors.toMap(p -> p.getFirst(), p -> p.getSecond()));
    }

    private static void saveEquations(@NotNull DataOutput out, HEquations eqs) throws IOException {
      out.writeBoolean(eqs.stable);
      DataInputOutputUtil.writeINT(out, eqs.results.size());
      for (DirectionResultPair pair : eqs.results) {
        DataInputOutputUtil.writeINT(out, pair.directionKey);
        HResult rhs = pair.hResult;
        if (rhs instanceof HFinal) {
          HFinal finalResult = (HFinal)rhs;
          out.writeBoolean(true); // final flag
          DataInputOutputUtil.writeINT(out, finalResult.value.ordinal());
        }
        else if (rhs instanceof HPending) {
          HPending pendResult = (HPending)rhs;
          out.writeBoolean(false); // pending flag
          DataInputOutputUtil.writeINT(out, pendResult.delta.length);

          for (HComponent component : pendResult.delta) {
            DataInputOutputUtil.writeINT(out, component.value.ordinal());
            HKey[] ids = component.ids;
            DataInputOutputUtil.writeINT(out, ids.length);
            for (HKey hKey : ids) {
              out.write(hKey.key);
              int rawDirKey = hKey.negated ? -hKey.dirKey : hKey.dirKey;
              DataInputOutputUtil.writeINT(out, rawDirKey);
              out.writeBoolean(hKey.stable);
            }
          }
        }
        else if (rhs instanceof HEffects) {
          HEffects effects = (HEffects)rhs;
          DataInputOutputUtil.writeINT(out, effects.effects.size());
          for (HEffectQuantum effect : effects.effects) {
            if (effect == HEffectQuantum.TopEffectQuantum) {
              DataInputOutputUtil.writeINT(out, -1);
            }
            else if (effect == HEffectQuantum.ThisChangeQuantum) {
              DataInputOutputUtil.writeINT(out, -2);
            }
            else if (effect instanceof HEffectQuantum.CallQuantum) {
              DataInputOutputUtil.writeINT(out, -3);
              HEffectQuantum.CallQuantum callQuantum = (HEffectQuantum.CallQuantum)effect;
              out.write(callQuantum.key.key);
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
            else if (effect instanceof HEffectQuantum.ParamChangeQuantum) {
              DataInputOutputUtil.writeINT(out, ((HEffectQuantum.ParamChangeQuantum)effect).n);
            }
          }
        }
      }
    }

    private static HEquations readEquations(@NotNull DataInput in) throws IOException {
      boolean stable = in.readBoolean();
      int size = DataInputOutputUtil.readINT(in);
      ArrayList<DirectionResultPair> results = new ArrayList<>(size);
      for (int k = 0; k < size; k++) {
        int directionKey = DataInputOutputUtil.readINT(in);
        Direction direction = Direction.fromInt(directionKey);
        if (direction == Direction.Pure) {
          Set<HEffectQuantum> effects = new HashSet<>();
          int effectsSize = DataInputOutputUtil.readINT(in);
          for (int i = 0; i < effectsSize; i++) {
            int effectMask = DataInputOutputUtil.readINT(in);
            if (effectMask == -1) {
              effects.add(HEffectQuantum.TopEffectQuantum);
            }
            else if (effectMask == -2) {
              effects.add(HEffectQuantum.ThisChangeQuantum);
            }
            else if (effectMask == -3){
              byte[] bytes = new byte[BytecodeAnalysisConverter.HASH_SIZE];
              in.readFully(bytes);
              int rawDirKey = DataInputOutputUtil.readINT(in);
              boolean isStable = in.readBoolean();
              HKey key = new HKey(bytes, Math.abs(rawDirKey), isStable, false);
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
              effects.add(new HEffectQuantum.CallQuantum(key, data, isStatic));
            }
            else {
              effects.add(new HEffectQuantum.ParamChangeQuantum(effectMask));
            }
          }
          results.add(new DirectionResultPair(directionKey, new HEffects(effects)));
        }
        else {
          boolean isFinal = in.readBoolean(); // flag
          if (isFinal) {
            int ordinal = DataInputOutputUtil.readINT(in);
            Value value = Value.values()[ordinal];
            results.add(new DirectionResultPair(directionKey, new HFinal(value)));
          }
          else {
            int sumLength = DataInputOutputUtil.readINT(in);
            HComponent[] components = new HComponent[sumLength];

            for (int i = 0; i < sumLength; i++) {
              int ordinal = DataInputOutputUtil.readINT(in);
              Value value = Value.values()[ordinal];
              int componentSize = DataInputOutputUtil.readINT(in);
              HKey[] ids = new HKey[componentSize];
              for (int j = 0; j < componentSize; j++) {
                byte[] bytes = new byte[BytecodeAnalysisConverter.HASH_SIZE];
                in.readFully(bytes);
                int rawDirKey = DataInputOutputUtil.readINT(in);
                ids[j] = new HKey(bytes, Math.abs(rawDirKey), in.readBoolean(), rawDirKey < 0);
              }
              components[i] = new HComponent(value, ids);
            }
            results.add(new DirectionResultPair(directionKey, new HPending(components)));
          }
        }
      }
      return new HEquations(results, stable);
    }
  }
}
