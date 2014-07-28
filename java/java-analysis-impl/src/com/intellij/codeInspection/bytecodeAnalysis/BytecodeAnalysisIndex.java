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
public class BytecodeAnalysisIndex extends FileBasedIndexExtension<Long, IdEquation> {
  public static final ID<Long, IdEquation> NAME = ID.create("bytecodeAnalysis");
  private final EquationExternalizer myExternalizer = new EquationExternalizer();
  private static final DataIndexer<Long, IdEquation, FileContent> INDEXER =
    new ClassDataIndexer(BytecodeAnalysisConverter.getInstance());
  private static final SmartLongKeyDescriptor KEY_DESCRIPTOR = new SmartLongKeyDescriptor();

  private static final int ourInternalVersion = 3;
  private static boolean ourEnabled = SystemProperties.getBooleanProperty("idea.enable.bytecode.contract.inference", isEnabledByDefault());

  private static boolean isEnabledByDefault() {
    Application application = ApplicationManager.getApplication();
    return application.isInternal() || application.isUnitTestMode();
  }

  @NotNull
  @Override
  public ID<Long, IdEquation> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<Long, IdEquation, FileContent> getIndexer() {
    return INDEXER;
  }

  @NotNull
  @Override
  public KeyDescriptor<Long> getKeyDescriptor() {
    return KEY_DESCRIPTOR;
  }

  @NotNull
  @Override
  public DataExternalizer<IdEquation> getValueExternalizer() {
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
    return ourInternalVersion + BytecodeAnalysisConverter.getInstance().getVersion() + (ourEnabled ? 0xFF : 0);
  }

  public static class EquationExternalizer implements DataExternalizer<IdEquation>, DifferentSerializableBytesImplyNonEqualityPolicy {
    @Override
    public void save(@NotNull DataOutput out, IdEquation equation) throws IOException {
      long id = equation.id;
      int sign = id > 0 ? 1 : -1;
      id = Math.abs(id);
      int primaryId = (int)(id / BytecodeAnalysisConverter.SHIFT);
      int secondaryId = (int)(id % BytecodeAnalysisConverter.SHIFT);
      out.writeInt(sign * primaryId);
      DataInputOutputUtil.writeINT(out, secondaryId);
      IdResult rhs = equation.rhs;
      if (rhs instanceof IdFinal) {
        IdFinal finalResult = (IdFinal)rhs;
        out.writeBoolean(true); // final flag
        DataInputOutputUtil.writeINT(out, finalResult.value.ordinal());
      } else {
        IdPending pendResult = (IdPending)rhs;
        out.writeBoolean(false); // pending flag
        DataInputOutputUtil.writeINT(out, pendResult.delta.length);

        for (IntIdComponent component : pendResult.delta) {
          DataInputOutputUtil.writeINT(out, component.value.ordinal());
          long[] ids = component.ids;
          DataInputOutputUtil.writeINT(out, ids.length);
          for (long id1 : ids) {
            sign = id1 > 0 ? 1 : -1;
            id = Math.abs(id1);
            primaryId = (int)(id / BytecodeAnalysisConverter.SHIFT);
            secondaryId = (int)(id % BytecodeAnalysisConverter.SHIFT);
            out.writeInt(sign * primaryId);
            DataInputOutputUtil.writeINT(out, secondaryId);
          }
        }
      }
    }

    @Override
    public IdEquation read(@NotNull DataInput in) throws IOException {
      long primaryId = in.readInt();
      int sign = primaryId > 0 ? 1 : -1;
      primaryId = Math.abs(primaryId);
      int secondaryId = DataInputOutputUtil.readINT(in);
      long equationId = sign * (primaryId * BytecodeAnalysisConverter.SHIFT + secondaryId);
      boolean isFinal = in.readBoolean(); // flag
      if (isFinal) {
        int ordinal = DataInputOutputUtil.readINT(in);
        Value value = Value.values()[ordinal];
        return new IdEquation(equationId, new IdFinal(value));
      } else {

        int sumLength = DataInputOutputUtil.readINT(in);
        IntIdComponent[] components = new IntIdComponent[sumLength];

        for (int i = 0; i < sumLength; i++) {
          int ordinal = DataInputOutputUtil.readINT(in);
          Value value = Value.values()[ordinal];
          int componentSize = DataInputOutputUtil.readINT(in);
          long[] ids = new long[componentSize];
          for (int j = 0; j < componentSize; j++) {
            primaryId = in.readInt();
            sign = primaryId > 0 ? 1 : -1;
            primaryId = Math.abs(primaryId);
            secondaryId = DataInputOutputUtil.readINT(in);
            long id = sign * (primaryId * BytecodeAnalysisConverter.SHIFT + secondaryId);
            ids[j] = id;
          }
          components[i] = new IntIdComponent(value, ids);
        }
        return new IdEquation(equationId, new IdPending(components));
      }
    }
  }

  private static class SmartLongKeyDescriptor implements KeyDescriptor<Long>, DifferentSerializableBytesImplyNonEqualityPolicy {
    @Override
    public void save(@NotNull DataOutput out, Long value) throws IOException {
      long id = value.longValue();
      int sign = id > 0 ? 1 : -1;
      id = Math.abs(id);
      int primaryId = (int)(id / BytecodeAnalysisConverter.SHIFT);
      int secondaryId = (int)(id % BytecodeAnalysisConverter.SHIFT);
      out.writeInt(primaryId * sign);
      DataInputOutputUtil.writeINT(out, secondaryId);
    }

    @Override
    public Long read(@NotNull DataInput in) throws IOException {
      long primaryId = in.readInt();
      int sign = primaryId > 0 ? 1 : -1;
      primaryId = Math.abs(primaryId);
      int secondaryId = DataInputOutputUtil.readINT(in);
      return sign * (primaryId * BytecodeAnalysisConverter.SHIFT + secondaryId);
    }

    @Override
    public int getHashCode(Long value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(Long val1, Long val2) {
      return val1.longValue() == val2.longValue();
    }
  }
}
