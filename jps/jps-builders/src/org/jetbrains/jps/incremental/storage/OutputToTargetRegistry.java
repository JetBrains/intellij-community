// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IntInlineKeyDescriptor;
import gnu.trove.TIntHashSet;
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
    @Override
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

    @Override
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
