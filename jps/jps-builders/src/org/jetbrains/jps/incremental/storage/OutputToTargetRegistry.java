// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 */
public final class OutputToTargetRegistry extends AbstractStateStorage<Integer, IntSet> {
  private final PathRelativizerService myRelativizer;

  private static final DataExternalizer<IntSet> DATA_EXTERNALIZER = new DataExternalizer<IntSet>() {
    @Override
    public void save(@NotNull final DataOutput out, IntSet value) throws IOException {
      IntIterator iterator = value.iterator();
      while (iterator.hasNext()) {
        out.writeInt(iterator.nextInt());
      }
    }

    @Override
    public IntSet read(@NotNull DataInput in) throws IOException {
      final IntSet result = new IntOpenHashSet();
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        result.add(in.readInt());
      }
      return result;
    }
  };

  OutputToTargetRegistry(File storePath, PathRelativizerService relativizer) throws IOException {
    super(storePath, EnumeratorIntegerDescriptor.INSTANCE, DATA_EXTERNALIZER);
    myRelativizer = relativizer;
  }

  void addMapping(String outputPath, int buildTargetId) throws IOException {
    addMapping(Collections.singleton(outputPath), buildTargetId);
  }

  void addMapping(Collection<String> outputPaths, int buildTargetId) throws IOException {
    final IntSet set = new IntOpenHashSet();
    set.add(buildTargetId);
    for (String outputPath : outputPaths) {
      appendData(FileUtil.pathHashCode(relativePath(outputPath)), set);
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
      final int key = FileUtil.pathHashCode(relativePath(outputPath));
      synchronized (myDataLock) {
        final IntSet state = getState(key);
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
      final int key = FileUtil.pathHashCode(relativePath(outputPath));
      synchronized (myDataLock) {
        final IntSet associatedTargets = getState(key);
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

  @NotNull
  private String relativePath(@NotNull String path) {
    return myRelativizer.toRelative(path);
  }
}
