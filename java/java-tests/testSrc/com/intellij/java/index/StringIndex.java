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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.KeyCollectionBasedForwardIndex;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 12, 2007
 */
public class StringIndex {
  private final MapReduceIndex<String, String, PathContentPair> myIndex;
  private volatile Exception myRebuildException;
  public StringIndex(String testName,
                     final IndexStorage<String, String> storage,
                     final PersistentHashMap<Integer, Collection<String>> inputIndex,
                     boolean failOnRebuildRequest)
    throws IOException {
    IndexId<String, String> id = IndexId.create(testName + "string_index");
    IndexExtension<String, String, PathContentPair> extension = new IndexExtension<String, String, PathContentPair>() {
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
        return new EnumeratorStringDescriptor();
      }

      @NotNull
      @Override
      public DataExternalizer<String> getValueExternalizer() {
        return new EnumeratorStringDescriptor();
      }

      @Override
      public int getVersion() {
        return 0;
      }
    };
    myIndex = new MapReduceIndex<String, String, PathContentPair>(extension, storage, new KeyCollectionBasedForwardIndex<String, String>(extension) {
      @NotNull
      @Override
      public PersistentHashMap<Integer, Collection<String>> createMap() {
        return inputIndex;
      }
    }) {
      @Override
      public void checkCanceled() {
        ProgressManager.checkCanceled();
      }

      @Override
      public void requestRebuild(@NotNull Exception ex) {
        if (failOnRebuildRequest) {
          Assert.fail();
        } else {
          myRebuildException = ex;
        }
      }

    };
  }

  public List<String> getFilesByWord(@NotNull String word) throws StorageException {
    return ContainerUtil.collect(myIndex.getData(word).getValueIterator());
  }
  
  public boolean update(final String path, @Nullable String content, @Nullable String oldContent) {
    return myIndex.update(Math.abs(path.hashCode()), toInput(path, content)).compute();
  }

  public void flush() throws StorageException {
    myIndex.flush();
  }

  public void dispose() {
    myIndex.dispose();
  }

  @Nullable
  private PathContentPair toInput(@NotNull String path, @Nullable String content) {
    return content != null ? new PathContentPair(path, content) : null;
  }

  public Exception getRebuildException() {
    return myRebuildException;
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

    public PathContentPair(final String path, final String content) {
      this.path = path;
      this.content = content;
    }
  }

  public MapReduceIndex<String, String, PathContentPair> getIndex() {
    return myIndex;
  }
}
