/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.jps.backwardRefs.index;

import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.LightRef;
import org.jetbrains.jps.backwardRefs.LightRefDescriptor;
import org.jetbrains.jps.backwardRefs.SignatureData;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class CompilerIndices {
  //TODO manage version separately
  public final static int VERSION = 5;

  public final static IndexId<LightRef, Integer> BACK_USAGES = IndexId.create("back.refs");
  public final static IndexId<LightRef, Collection<LightRef>> BACK_HIERARCHY = IndexId.create("back.hierarchy");
  public final static IndexId<LightRef, Void> BACK_CLASS_DEF = IndexId.create("back.class.def");
  public final static IndexId<SignatureData, Collection<LightRef>> BACK_MEMBER_SIGN = IndexId.create("back.member.sign");
  public final static IndexId<LightRef, Collection<LightRef>> BACK_CAST = IndexId.create("back.cast");

  public static List<IndexExtension<?, ?, CompiledFileData>> getIndices() {
    return Arrays.asList(createBackwardClassDefinitionExtension(),
                         createBackwardUsagesExtension(),
                         createBackwardHierarchyExtension(),
                         createBackwardSignatureExtension(),
                         createBackwardCastExtension());
  }

  private static IndexExtension<LightRef, Collection<LightRef>, CompiledFileData> createBackwardCastExtension() {
    return new IndexExtension<LightRef, Collection<LightRef>, CompiledFileData>() {
      @NotNull
      @Override
      public IndexId<LightRef, Collection<LightRef>> getName() {
        return BACK_CAST;
      }

      @NotNull
      @Override
      public DataIndexer<LightRef, Collection<LightRef>, CompiledFileData> getIndexer() {
        return CompiledFileData::getCasts;
      }

      @NotNull
      @Override
      public KeyDescriptor<LightRef> getKeyDescriptor() {
        return LightRefDescriptor.INSTANCE;
      }

      @NotNull
      @Override
      public DataExternalizer<Collection<LightRef>> getValueExternalizer() {
        return createLightRefSeqExternalizer();
      }

      @Override
      public int getVersion() {
        return VERSION;
      }
    };
  }

  private static IndexExtension<LightRef, Integer, CompiledFileData> createBackwardUsagesExtension() {
    return new IndexExtension<LightRef, Integer, CompiledFileData>() {
      @Override
      public int getVersion() {
        return VERSION;
      }

      @NotNull
      public IndexId<LightRef, Integer> getName() {
        return BACK_USAGES;
      }

      @NotNull
      public DataIndexer<LightRef, Integer, CompiledFileData> getIndexer() {
        return CompiledFileData::getReferences;
      }

      @NotNull
      public KeyDescriptor<LightRef> getKeyDescriptor() {
        return LightRefDescriptor.INSTANCE;
      }

      @NotNull
      public DataExternalizer<Integer> getValueExternalizer() {
        return new UnsignedByteExternalizer();
      }
    };
  }

  private static class UnsignedByteExternalizer implements DataExternalizer<Integer> {
    @Override
    public void save(@NotNull DataOutput out, Integer value) throws IOException {
      int v = value;
      if (v > 255) {
        v = 255;
      }
      out.writeByte(v);
    }

    @Override
    public Integer read(@NotNull DataInput in) throws IOException {
      return in.readByte() & 0xFF;
    }
  }

  private static IndexExtension<LightRef, Collection<LightRef>, CompiledFileData> createBackwardHierarchyExtension() {
    return new IndexExtension<LightRef, Collection<LightRef>, CompiledFileData>() {
      @Override
      public int getVersion() {
        return VERSION;
      }

      @NotNull
      public IndexId<LightRef, Collection<LightRef>> getName() {
        return BACK_HIERARCHY;
      }

      @NotNull
      public DataIndexer<LightRef, Collection<LightRef>, CompiledFileData> getIndexer() {
        return CompiledFileData::getBackwardHierarchy;
      }

      @NotNull
      public KeyDescriptor<LightRef> getKeyDescriptor() {
        return LightRefDescriptor.INSTANCE;
      }

      @NotNull
      public DataExternalizer<Collection<LightRef>> getValueExternalizer() {
        return createLightRefSeqExternalizer();
      }
    };
  }

  private static IndexExtension<LightRef, Void, CompiledFileData> createBackwardClassDefinitionExtension() {
    return new IndexExtension<LightRef, Void, CompiledFileData>() {
      @Override
      public int getVersion() {
        return VERSION;
      }

      @NotNull
      public IndexId<LightRef, Void> getName() {
        return BACK_CLASS_DEF;
      }

      @NotNull
      public DataIndexer<LightRef, Void, CompiledFileData> getIndexer() {
        return CompiledFileData::getDefinitions;
      }

      @NotNull
      public KeyDescriptor<LightRef> getKeyDescriptor() {
        return LightRefDescriptor.INSTANCE;
      }

      @NotNull
      public DataExternalizer<Void> getValueExternalizer() {
        return VoidDataExternalizer.INSTANCE;
      }
    };
  }

  private static IndexExtension<SignatureData, Collection<LightRef>, CompiledFileData> createBackwardSignatureExtension() {
    return new IndexExtension<SignatureData, Collection<LightRef>, CompiledFileData>() {
      @NotNull
      @Override
      public IndexId<SignatureData, Collection<LightRef>> getName() {
        return BACK_MEMBER_SIGN;
      }

      @NotNull
      @Override
      public DataIndexer<SignatureData, Collection<LightRef>, CompiledFileData> getIndexer() {
        return CompiledFileData::getSignatureData;
      }

      @NotNull
      @Override
      public KeyDescriptor<SignatureData> getKeyDescriptor() {
        return createSignatureDataDescriptor();
      }

      @NotNull
      @Override
      public DataExternalizer<Collection<LightRef>> getValueExternalizer() {
        return createLightRefSeqExternalizer();
      }

      @Override
      public int getVersion() {
        return VERSION;
      }
    };
  }

  @NotNull
  private static DataExternalizer<Collection<LightRef>> createLightRefSeqExternalizer() {
    return new DataExternalizer<Collection<LightRef>>() {
      @Override
      public void save(@NotNull final DataOutput out, Collection<LightRef> value) throws IOException {
        DataInputOutputUtilRt.writeSeq(out, value, lightRef -> LightRefDescriptor.INSTANCE.save(out, lightRef));
      }

      @Override
      public Collection<LightRef> read(@NotNull final DataInput in) throws IOException {
        return DataInputOutputUtilRt.readSeq(in, () -> LightRefDescriptor.INSTANCE.read(in));
      }
    };
  }

  private static KeyDescriptor<SignatureData> createSignatureDataDescriptor() {
    return new KeyDescriptor<SignatureData>() {
      @Override
      public int getHashCode(SignatureData value) {
        return value.hashCode();
      }

      @Override
      public boolean isEqual(SignatureData val1, SignatureData val2) {
        return val1.equals(val2);
      }

      @Override
      public void save(@NotNull DataOutput out, SignatureData value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.getRawReturnType());
        out.writeByte(value.getIteratorKind());
        out.writeBoolean(value.isStatic());
      }

      @Override
      public SignatureData read(@NotNull DataInput in) throws IOException {
        return new SignatureData(DataInputOutputUtil.readINT(in), in.readByte(), in.readBoolean());
      }
    };
  }
}
