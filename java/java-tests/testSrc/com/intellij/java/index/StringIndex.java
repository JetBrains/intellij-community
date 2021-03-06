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
package com.intellij.java.index;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.MathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.indexing.impl.forward.KeyCollectionForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.PersistentMapBasedForwardIndex;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class StringIndex {
  private final MapReduceIndex<String, String, PathContentPair> myIndex;
  private volatile Throwable myRebuildThrowable;
  public StringIndex(String testName,
                     IndexStorage<String, String> storage,
                     File forwardIndexFile,
                     boolean failOnRebuildRequest)
    throws IOException {
    IndexId<String, String> id = IndexId.create(testName + "string_index");
    IndexExtension<String, String, PathContentPair> extension = new IndexExtension<>() {
      @NotNull
      @Override
      public IndexId<String, String> getName() {
        return id;
      }

      @NotNull
      @Override
      public DataIndexer<String, String, PathContentPair> getIndexer() {
        return new Indexer();
      }

      @NotNull
      @Override
      public KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
      }

      @NotNull
      @Override
      public DataExternalizer<String> getValueExternalizer() {
        return EnumeratorStringDescriptor.INSTANCE;
      }

      @Override
      public int getVersion() {
        return 0;
      }
    };

    DataExternalizer<Collection<String>> anotherStringExternalizer = new DataExternalizer<>() {
      @Override
      public void save(@NotNull DataOutput out, Collection<String> value)
        throws IOException {
        DataInputOutputUtil.writeINT(out, value.size());
        for (String key : value) {
          out.writeUTF(key);
        }
      }

      @Override
      public Collection<String> read(@NotNull DataInput _in)
        throws IOException {
        final int size = DataInputOutputUtil.readINT(_in);
        final List<String> list = new ArrayList<>();
        for (int idx = 0; idx < size; idx++) {
          list.add(_in.readUTF());
        }
        return list;
      }
    };
    myIndex = new MapReduceIndex<>(extension,
                                   storage,
                                   new PersistentMapBasedForwardIndex(forwardIndexFile.toPath(), false),
                                   new KeyCollectionForwardIndexAccessor<>(anotherStringExternalizer)) {
      @Override
      public void checkCanceled() {
        ProgressManager.checkCanceled();
      }

      @Override
      public void requestRebuild(@NotNull Throwable ex) {
        if (failOnRebuildRequest) {
          ex.printStackTrace();
          Assert.fail("Rebuild is not expected in this test");
        }
        else {
          myRebuildThrowable = ex;
        }
      }
    };
  }

  public List<String> getFilesByWord(@NotNull String word) throws StorageException {
    return ContainerUtil.collect(myIndex.getData(word).getValueIterator());
  }
  
  public boolean update(@NotNull String path, @Nullable String content) {
    int inputId = MathUtil.nonNegativeAbs(path.hashCode());
    PathContentPair contentPair = toInput(path, content);
    return myIndex.mapInputAndPrepareUpdate(inputId, contentPair).compute();
  }

  public long getModificationStamp() {
    return myIndex.getModificationStamp();
  }

  public void clear() {
    myIndex.clear();
  }

  public void dispose() {
    myIndex.dispose();
  }

  @Nullable
  private static PathContentPair toInput(@NotNull String path, @Nullable String content) {
    return content != null ? new PathContentPair(path, content) : null;
  }

  public Throwable getRebuildThrowable() {
    return myRebuildThrowable;
  }

  private static class Indexer implements DataIndexer<String, String, PathContentPair> {
    @Override
    @NotNull
    public Map<String,String> map(@NotNull final PathContentPair inputData) {
      final Map<String,String> _map = new HashMap<>();
      final StringBuilder builder = new StringBuilder();
      final String content = inputData.content;
      for (int idx = 0; idx < content.length(); idx++) {
        final char ch = content.charAt(idx);
        if (Character.isWhitespace(ch)) {
          if (builder.length() > 0) {
            _map.put(builder.toString(), inputData.path);
            builder.setLength(0);
          }
        }
        else {
          builder.append(ch);
        }
      }
      // emit the last word
      if (builder.length() > 0) {
        _map.put(builder.toString(), inputData.path);
        builder.setLength(0);
      }
      return _map;
    }
  }
  
  private static final class PathContentPair {
    final String path;
    final String content;

    PathContentPair(final String path, final String content) {
      this.path = path;
      this.content = content;
    }
  }

  public MapReduceIndex<String, String, PathContentPair> getIndex() {
    return myIndex;
  }
}
