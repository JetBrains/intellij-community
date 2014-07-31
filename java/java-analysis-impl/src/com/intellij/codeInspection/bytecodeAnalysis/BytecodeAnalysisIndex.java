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

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
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

/**
 * @author lambdamix
 */
public class BytecodeAnalysisIndex extends FileBasedIndexExtension<HKey, HResult> {
  public static final ID<HKey, HResult> NAME = ID.create("bytecodeAnalysis");
  private final HEquationExternalizer myExternalizer = new HEquationExternalizer();
  private static final ClassDataIndexer INDEXER = new ClassDataIndexer();
  private static final HKeyDescriptor KEY_DESCRIPTOR = new HKeyDescriptor();

  private static final int ourInternalVersion = 3;
  private static boolean ourEnabled = SystemProperties.getBooleanProperty("idea.enable.bytecode.contract.inference", isEnabledByDefault());

  private static boolean isEnabledByDefault() {
    Application application = ApplicationManager.getApplication();
    return application.isInternal() || application.isUnitTestMode();
  }

  @NotNull
  @Override
  public ID<HKey, HResult> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<HKey, HResult, FileContent> getIndexer() {
    return INDEXER;
  }

  @NotNull
  @Override
  public KeyDescriptor<HKey> getKeyDescriptor() {
    return KEY_DESCRIPTOR;
  }

  @NotNull
  @Override
  public DataExternalizer<HResult> getValueExternalizer() {
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

  private static class HKeyDescriptor implements KeyDescriptor<HKey>, DifferentSerializableBytesImplyNonEqualityPolicy {

    @Override
    public void save(@NotNull DataOutput out, HKey value) throws IOException {
      out.write(value.key);
      DataInputOutputUtil.writeINT(out, value.dirKey);
      out.writeBoolean(value.stable);
    }

    @Override
    public HKey read(@NotNull DataInput in) throws IOException {
      byte[] bytes = new byte[BytecodeAnalysisConverter.HASH_SIZE];
      for (int i = 0; i < bytes.length; i++) {
        bytes[i] = in.readByte();
      }
      return new HKey(bytes, DataInputOutputUtil.readINT(in), in.readBoolean());
    }

    @Override
    public int getHashCode(HKey value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(HKey val1, HKey val2) {
      return val1.equals(val2);
    }
  }

  public static class HEquationExternalizer implements DataExternalizer<HResult>, DifferentSerializableBytesImplyNonEqualityPolicy {
    @Override
    public void save(@NotNull DataOutput out, HResult rhs) throws IOException {
      if (rhs instanceof HFinal) {
        HFinal finalResult = (HFinal)rhs;
        out.writeBoolean(true); // final flag
        DataInputOutputUtil.writeINT(out, finalResult.value.ordinal());
      } else {
        HPending pendResult = (HPending)rhs;
        out.writeBoolean(false); // pending flag
        DataInputOutputUtil.writeINT(out, pendResult.delta.length);

        for (HComponent component : pendResult.delta) {
          DataInputOutputUtil.writeINT(out, component.value.ordinal());
          HKey[] ids = component.ids;
          DataInputOutputUtil.writeINT(out, ids.length);
          for (HKey id1 : ids) {
            KEY_DESCRIPTOR.save(out, id1);
          }
        }
      }
    }

    @Override
    public HResult read(@NotNull DataInput in) throws IOException {
      boolean isFinal = in.readBoolean(); // flag
      if (isFinal) {
        int ordinal = DataInputOutputUtil.readINT(in);
        Value value = Value.values()[ordinal];
        return new HFinal(value);
      } else {

        int sumLength = DataInputOutputUtil.readINT(in);
        HComponent[] components = new HComponent[sumLength];

        for (int i = 0; i < sumLength; i++) {
          int ordinal = DataInputOutputUtil.readINT(in);
          Value value = Value.values()[ordinal];
          int componentSize = DataInputOutputUtil.readINT(in);
          HKey[] ids = new HKey[componentSize];
          for (int j = 0; j < componentSize; j++) {
            ids[j] = KEY_DESCRIPTOR.read(in);
          }
          components[i] = new HComponent(value, ids);
        }
        return new HPending(components);
      }
    }
  }
}
