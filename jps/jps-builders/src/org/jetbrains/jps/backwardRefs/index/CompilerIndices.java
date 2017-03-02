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
package org.jetbrains.jps.backwardRefs.index;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.LightRef;
import org.jetbrains.jps.backwardRefs.LightRefDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CompilerIndices {
  //TODO manage version separately
  public final static int VERSION = 3;

  public final static ID<LightRef, Integer> BACK_USAGES = ID.create("back.refs");
  public final static ID<LightRef, Collection<LightRef>> BACK_HIERARCHY = ID.create("back.hierarchy");
  //TODO looks like a hack
  public final static ID<LightRef, Boolean> BACK_CLASS_DEF = ID.create("back.class.def");

  public static List<IndexExtension<LightRef, ?, CompiledFileData>> getIndices() {
    return ContainerUtil.list(createBackwardClassDefinitionExtension(), createBackwardUsagesExtension(), createBackwardHierarchyExtension());
  }

  private static IndexExtension<LightRef, Integer, CompiledFileData> createBackwardUsagesExtension() {
    return new IndexExtension<LightRef, Integer, CompiledFileData>() {
      @Override
      public int getVersion() {
        return VERSION;
      }

      @NotNull
      public ID<LightRef, Integer> getName() {
        return BACK_USAGES;
      }

      @NotNull
      public DataIndexer<LightRef, Integer, CompiledFileData> getIndexer() {
        return new DataIndexer<LightRef, Integer, CompiledFileData>() {
          @NotNull
          @Override
          public Map<LightRef, Integer> map(@NotNull CompiledFileData inputData) {
            return inputData.getReferences();
          }
        };
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
      public ID<LightRef, Collection<LightRef>> getName() {
        return BACK_HIERARCHY;
      }

      @NotNull
      public DataIndexer<LightRef, Collection<LightRef>, CompiledFileData> getIndexer() {
        return new DataIndexer<LightRef, Collection<LightRef>, CompiledFileData>() {
          @NotNull
          @Override
          public Map<LightRef, Collection<LightRef>> map(@NotNull CompiledFileData inputData) {
            return inputData.getBackwardHierarchy();
          }
        };
      }

      @NotNull
      public KeyDescriptor<LightRef> getKeyDescriptor() {
        return LightRefDescriptor.INSTANCE;
      }

      @NotNull
      public DataExternalizer<Collection<LightRef>> getValueExternalizer() {
        return new DataExternalizer<Collection<LightRef>>() {
          @Override
          public void save(@NotNull final DataOutput out, Collection<LightRef> value) throws IOException {
            DataInputOutputUtil.writeSeq(out, value, new ThrowableConsumer<LightRef, IOException>() {
              @Override
              public void consume(LightRef lightRef) throws IOException {
                LightRefDescriptor.INSTANCE.save(out, lightRef);
              }
            });
          }

          @Override
          public Collection<LightRef> read(@NotNull final DataInput in) throws IOException {
            return DataInputOutputUtil.readSeq(in, new ThrowableComputable<LightRef, IOException>() {
              @Override
              public LightRef compute() throws IOException {
                return LightRefDescriptor.INSTANCE.read(in);
              }
            });
          }
        };
      }
    };
  }

  private static IndexExtension<LightRef, Boolean, CompiledFileData> createBackwardClassDefinitionExtension() {
    return new IndexExtension<LightRef, Boolean, CompiledFileData>() {
      @Override
      public int getVersion() {
        return VERSION;
      }

      @NotNull
      public ID<LightRef, Boolean> getName() {
        return BACK_CLASS_DEF;
      }

      @NotNull
      public DataIndexer<LightRef, Boolean, CompiledFileData> getIndexer() {
        return new DataIndexer<LightRef, Boolean, CompiledFileData>() {
          @NotNull
          @Override
          public Map<LightRef, Boolean> map(@NotNull CompiledFileData inputData) {
            return inputData.getDefinitions();
          }
        };
      }

      @NotNull
      public KeyDescriptor<LightRef> getKeyDescriptor() {
        return LightRefDescriptor.INSTANCE;
      }

      @NotNull
      public DataExternalizer<Boolean> getValueExternalizer() {
        return new DataExternalizer<Boolean>() {
          @Override
          public void save(@NotNull DataOutput out, Boolean value) throws IOException {
            out.writeBoolean(value);
          }

          @Override
          public Boolean read(@NotNull DataInput in) throws IOException {
            return in.readBoolean();
          }
        };
      }
    };
  }

}
