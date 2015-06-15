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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DifferentSerializableBytesImplyNonEqualityPolicy;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisIndex extends FileBasedIndexExtension<Bytes, HEquations> {
  public static final ID<Bytes, HEquations> NAME = ID.create("bytecodeAnalysis");
  private final HEquationsExternalizer myExternalizer = new HEquationsExternalizer();
  private static final ClassDataIndexer INDEXER = new ClassDataIndexer();
  private static final HKeyDescriptor KEY_DESCRIPTOR = new HKeyDescriptor();

  private static final int ourInternalVersion = 4;
  private static final boolean ourEnabled = SystemProperties.getBooleanProperty("idea.enable.bytecode.contract.inference", true);

  @NotNull
  @Override
  public ID<Bytes, HEquations> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<Bytes, HEquations, FileContent> getIndexer() {
    return INDEXER;
  }

  @NotNull
  @Override
  public KeyDescriptor<Bytes> getKeyDescriptor() {
    return KEY_DESCRIPTOR;
  }

  @NotNull
  @Override
  public DataExternalizer<HEquations> getValueExternalizer() {
    return myExternalizer;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(JavaClassFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return ourEnabled && super.acceptInput(file);
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return ourInternalVersion + (ourEnabled ? 0xFF : 0);
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
      for (int i = 0; i < bytes.length; i++) {
        bytes[i] = in.readByte();
      }
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
  public static class HEquationsExternalizer implements DataExternalizer<HEquations>, DifferentSerializableBytesImplyNonEqualityPolicy {
    @Override
    public void save(@NotNull DataOutput out, HEquations eqs) throws IOException {
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
        else {
          HPending pendResult = (HPending)rhs;
          out.writeBoolean(false); // pending flag
          DataInputOutputUtil.writeINT(out, pendResult.delta.length);

          for (HComponent component : pendResult.delta) {
            DataInputOutputUtil.writeINT(out, component.value.ordinal());
            HKey[] ids = component.ids;
            DataInputOutputUtil.writeINT(out, ids.length);
            for (HKey hKey : ids) {
              out.write(hKey.key);
              DataInputOutputUtil.writeINT(out, hKey.dirKey);
              out.writeBoolean(hKey.stable);
            }
          }
        }
      }
    }

    @Override
    public HEquations read(@NotNull DataInput in) throws IOException {
      boolean stable = in.readBoolean();
      int size = DataInputOutputUtil.readINT(in);
      ArrayList<DirectionResultPair> results = new ArrayList<DirectionResultPair>(size);
      for (int k = 0; k < size; k++) {
        int directionKey = DataInputOutputUtil.readINT(in);
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
              for (int bi = 0; bi < bytes.length; bi++) {
                bytes[bi] = in.readByte();
              }
              ids[j] = new HKey(bytes, DataInputOutputUtil.readINT(in), in.readBoolean());
            }
            components[i] = new HComponent(value, ids);
          }
          results.add(new DirectionResultPair(directionKey, new HPending(components)));
        }
      }
      return new HEquations(results, stable);
    }
  }
}
