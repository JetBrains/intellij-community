// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 * @since 11.10.2012
 */
public class OneToManyPathsMapping extends AbstractStateStorage<String, Collection<String>> {
  public OneToManyPathsMapping(File storePath) throws IOException {
    super(storePath, PathStringDescriptor.INSTANCE, new PathCollectionExternalizer());
  }

  @Override
  public void update(@NotNull String keyPath, @NotNull Collection<String> boundPaths) throws IOException {
    super.update(FileUtil.toSystemIndependentName(keyPath), normalizePaths(boundPaths));
  }

  public final void update(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    super.update(FileUtil.toSystemIndependentName(keyPath), Collections.singleton(FileUtil.toSystemIndependentName(boundPath)));
  }

  public final void appendData(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    super.appendData(FileUtil.toSystemIndependentName(keyPath), Collections.singleton(FileUtil.toSystemIndependentName(boundPath)));
  }

  @Override
  public void appendData(@NotNull String keyPath, @NotNull Collection<String> boundPaths) throws IOException {
    super.appendData(FileUtil.toSystemIndependentName(keyPath), normalizePaths(boundPaths));
  }

  @Nullable
  @Override
  public Collection<String> getState(@NotNull String keyPath) throws IOException {
    return super.getState(FileUtil.toSystemIndependentName(keyPath));
  }

  @Override
  public void remove(@NotNull String keyPath) throws IOException {
    super.remove(FileUtil.toSystemIndependentName(keyPath));
  }

  public final void removeData(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    final Collection<String> outputPaths = getState(FileUtil.toSystemIndependentName(keyPath));
    if (outputPaths != null) {
      final boolean removed = outputPaths.remove(FileUtil.toSystemIndependentName(boundPath));
      if (outputPaths.isEmpty()) {
        remove(keyPath);
      }
      else {
        if (removed) {
          update(keyPath, outputPaths);
        }
      }
    }
  }

  private static class PathCollectionExternalizer implements DataExternalizer<Collection<String>> {
    @Override
    public void save(@NotNull DataOutput out, Collection<String> value) throws IOException {
      for (String str : value) {
        IOUtil.writeUTF(out, str);
      }
    }

    @Override
    public Collection<String> read(@NotNull DataInput in) throws IOException {
      final Set<String> result = new THashSet<>(FileUtil.PATH_HASHING_STRATEGY);
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        final String str = IOUtil.readUTF(stream);
        result.add(str);
      }
      return result;
    }
  }

  private static Collection<String> normalizePaths(Collection<String> outputs) {
    Collection<String> normalized = new ArrayList<>(outputs.size());
    for (String out : outputs) {
      normalized.add(FileUtil.toSystemIndependentName(out));
    }
    return normalized;
  }

}
