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
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IntInlineKeyDescriptor;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 *         Date: 10-Apr-14
 */
public class OutputToSourceRegistry extends AbstractStateStorage<Integer, TIntHashSet>{
  private static final DataExternalizer<TIntHashSet> DATA_EXTERNALIZER = new DataExternalizer<TIntHashSet>() {
    public void save(@NotNull final DataOutput out, TIntHashSet value) throws IOException {
      final Ref<IOException> exRef = Ref.create(null);
      value.forEach(new TIntProcedure() {
        public boolean execute(int value) {
          try {
            out.writeInt(value);
          }
          catch (IOException e) {
            exRef.set(e);
            return false;
          }
          return true;
        }
      });
      final IOException error = exRef.get();
      if (error != null) {
        throw error;
      }
    }

    public TIntHashSet read(@NotNull DataInput in) throws IOException {
      final TIntHashSet result = new TIntHashSet();
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        result.add(in.readInt());
      }
      return result;
    }
  };
  
  OutputToSourceRegistry(@NonNls File storePath) throws IOException {
    super(storePath, new IntInlineKeyDescriptor(), DATA_EXTERNALIZER);
  }
  
  protected void addMapping(String outputPath, String sourcePath) throws IOException {
    addMapping(Collections.singleton(outputPath), sourcePath);
  }
  
  protected void addMapping(Collection<String> outputPaths, String sourcePath) throws IOException {
    final TIntHashSet set = new TIntHashSet();
    set.add(FileUtil.pathHashCode(sourcePath));
    for (String outputPath : outputPaths) {
      appendData(FileUtil.pathHashCode(outputPath), set);
    }
  }

  public void removeMapping(String outputPath, String sourcePath) throws IOException {
    removeMapping(Collections.singleton(outputPath), sourcePath);
  }

  public void removeMapping(Collection<String> outputPaths, String sourcePath) throws IOException {
    if (outputPaths.isEmpty()) {
      return;
    }
    final int value = FileUtil.pathHashCode(sourcePath);
    for (String outputPath : outputPaths) {
      final int key = FileUtil.pathHashCode(outputPath);
      synchronized (myDataLock) {
        final TIntHashSet state = getState(key);
        if (state != null) {
          final boolean removed = state.remove(value);
          if (state.isEmpty()) {
            remove(key);
          }
          else {
            if (removed) {
              update(key, state);
            }
          }
        }
      }
    }
  }
  
  public Collection<String> getSafeToDeleteOutputs(Collection<String> outputPaths, String associatedSourcePath) throws IOException {
    final int size = outputPaths.size();
    if (size == 0) {
      return outputPaths;
    }
    final Collection<String> result = new ArrayList<String>(size);
    Integer cached = null;
    for (String outputPath : outputPaths) {
      final int key = FileUtil.pathHashCode(outputPath);
      synchronized (myDataLock) {
        final TIntHashSet associatedSources = getState(key);
        if (associatedSources == null || associatedSources.size() != 1) {
          continue;
        }
        final int srcHash = cached == null? (cached = FileUtil.pathHashCode(associatedSourcePath)) : cached.intValue();
        if (associatedSources.contains(srcHash)) {
          result.add(outputPath);
        }
      }
    }
    return result;
  }
}
