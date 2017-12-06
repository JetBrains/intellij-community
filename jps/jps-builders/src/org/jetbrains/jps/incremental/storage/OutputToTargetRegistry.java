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
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 */
public class OutputToTargetRegistry extends AbstractStateStorage<Integer, TIntHashSet>{
  private static final DataExternalizer<TIntHashSet> DATA_EXTERNALIZER = new DataExternalizer<TIntHashSet>() {
    public void save(@NotNull final DataOutput out, TIntHashSet value) throws IOException {
      final Ref<IOException> exRef = Ref.create(null);
      value.forEach(value1 -> {
        try {
          out.writeInt(value1);
        }
        catch (IOException e) {
          exRef.set(e);
          return false;
        }
        return true;
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
  
  OutputToTargetRegistry(File storePath) throws IOException {
    super(storePath, new IntInlineKeyDescriptor(), DATA_EXTERNALIZER);
  }
  
  protected void addMapping(String outputPath, int buildTargetId) throws IOException {
    addMapping(Collections.singleton(outputPath), buildTargetId);
  }
  
  protected void addMapping(Collection<String> outputPaths, int buildTargetId) throws IOException {
    final TIntHashSet set = new TIntHashSet();
    set.add(buildTargetId);
    for (String outputPath : outputPaths) {
      appendData(FileUtil.pathHashCode(outputPath), set);
    }
  }

  public void removeMapping(String outputPath, int buildTargetId) throws IOException {
    removeMapping(Collections.singleton(outputPath), buildTargetId);
  }

  public void removeMapping(Collection<String> outputPaths, int buildTargetId) throws IOException {
    if (outputPaths.isEmpty()) {
      return;
    }
    for (String outputPath : outputPaths) {
      final int key = FileUtil.pathHashCode(outputPath);
      synchronized (myDataLock) {
        final TIntHashSet state = getState(key);
        if (state != null) {
          final boolean removed = state.remove(buildTargetId);
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
  
  public Collection<String> getSafeToDeleteOutputs(Collection<String> outputPaths, int currentTargetId) throws IOException {
    final int size = outputPaths.size();
    if (size == 0) {
      return outputPaths;
    }
    final Collection<String> result = new ArrayList<>(size);
    for (String outputPath : outputPaths) {
      final int key = FileUtil.pathHashCode(outputPath);
      synchronized (myDataLock) {
        final TIntHashSet associatedTargets = getState(key);
        if (associatedTargets == null || associatedTargets.size() != 1) {
          continue;
        }
        if (associatedTargets.contains(currentTargetId)) {
          result.add(outputPath);
        }
      }
    }
    return result;
  }
}
