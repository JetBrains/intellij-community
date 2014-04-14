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

/*
 * @author max
 */
package com.intellij.find.ngrams;

import com.intellij.openapi.util.ThreadLocalCachedByteArray;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.util.indexing.*;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Collection;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;
import java.util.zip.DeflaterOutputStream;

public class TrigramIndex extends ScalarIndexExtension<Integer> implements CustomInputsIndexFileBasedIndexExtension<Integer> {
  public static final boolean ENABLED = "true".equals(System.getProperty("idea.internal.trigramindex.enabled"));

  public static final ID<Integer,Void> INDEX_ID = ID.create("Trigram.Index");

  private static final FileBasedIndex.InputFilter INPUT_FILTER = new FileBasedIndex.InputFilter() {
    @Override
    public boolean acceptInput(@NotNull VirtualFile file) {
      return !file.getFileType().isBinary();
    }
  };
  private static final FileBasedIndex.InputFilter NO_FILES = new FileBasedIndex.InputFilter() {
    @Override
    public boolean acceptInput(@NotNull VirtualFile file) {
      return false;
    }
  };

  @NotNull
  @Override
  public ID<Integer, Void> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<Integer, Void, FileContent> getIndexer() {
    return new DataIndexer<Integer, Void, FileContent>() {
      @Override
      @NotNull
      public Map<Integer, Void> map(@NotNull FileContent inputData) {
        final Map<Integer, Void> result = new THashMap<Integer, Void>();
        TIntHashSet built = TrigramBuilder.buildTrigram(inputData.getContentAsText());
        built.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int value) {
            result.put(value, null);
            return true;
          }
        });
        return result;
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<Integer> getKeyDescriptor() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    if (ENABLED) {
      return INPUT_FILTER;
    }
    else {
      return NO_FILES;
    }
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return ENABLED ? 2 + (IdIndex.ourSnapshotMappingsEnabled ? 0xFF:0) : 1;
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }

  private static final ThreadLocalCachedByteArray spareBufferLocal = new ThreadLocalCachedByteArray();

  @NotNull
  @Override
  public DataExternalizer<Collection<Integer>> createExternalizer() {
    return new InputIndexDataExternalizer<Integer>(getKeyDescriptor(), INDEX_ID) {
      @Override
      public void save(@NotNull DataOutput out, @NotNull Collection<Integer> value) throws IOException {
        final int maxSerializedLength = 4 * value.size() + 4;
        byte[] originalBuffer = spareBufferLocal.getBuffer(maxSerializedLength);
        UnsyncByteArrayOutputStream originalBytes = new UnsyncByteArrayOutputStream(originalBuffer);
        DataOutputStream originalDataOutput = new DataOutputStream(originalBytes);

        super.save(originalDataOutput, value);
        final int size = originalBytes.size();
        DataInputOutputUtil.writeINT(out, size);

        Deflater deflater = new Deflater(Deflater.HUFFMAN_ONLY);
        DeflaterOutputStream compressedDeflaterOutput = new DeflaterOutputStream((OutputStream)out, deflater);
        try {
          compressedDeflaterOutput.write(originalBuffer, 0, size);
        } finally {
          try {
            compressedDeflaterOutput.close();
          } catch (IOException ignore) {}
          deflater.end();
        }
      }

      @NotNull
      @Override
      public Collection<Integer> read(@NotNull DataInput in) throws IOException {
        byte[] originalBuffer;
        int size;
        Deflater deflater = new Deflater(Deflater.HUFFMAN_ONLY);
        DeflaterInputStream is = new DeflaterInputStream((DataInputStream)in, deflater);
        try {
          size = DataInputOutputUtil.readINT(in);
          originalBuffer = spareBufferLocal.getBuffer(size);
          //noinspection ResultOfMethodCallIgnored
          is.read(originalBuffer, 0, size);
        }
        finally {
          try {
            is.close();
          } catch (IOException ignore) {}
          deflater.end();
        }

        return super.read(new DataInputStream(new UnsyncByteArrayInputStream(originalBuffer, 0, size)));
      }
    };
  }
}
