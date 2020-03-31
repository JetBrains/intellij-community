// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.JBIterator;
import com.intellij.util.containers.ObjectLinkedOpenHashSet;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.*;
import java.util.*;

import static com.intellij.util.containers.ContainerUtil.map;

/**
 * @author Eugene Zhuravlev
 */
public class OneToManyPathsMapping extends AbstractStateStorage<String, Collection<String>> {
  private final PathRelativizerService myRelativizer;

  public OneToManyPathsMapping(File storePath, PathRelativizerService relativizer) throws IOException {
    super(storePath, PathStringDescriptor.INSTANCE, new PathCollectionExternalizer());
    myRelativizer = relativizer;
  }

  @Override
  public void update(@NotNull String keyPath, @NotNull Collection<String> boundPaths) throws IOException {
    super.update(normalizePath(keyPath), normalizePaths(boundPaths));
  }

  public final void update(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    super.update(normalizePath(keyPath), Collections.singleton(normalizePath(boundPath)));
  }

  public final void appendData(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    super.appendData(normalizePath(keyPath), Collections.singleton(normalizePath(boundPath)));
  }

  @Override
  public void appendData(@NotNull String keyPath, @NotNull Collection<String> boundPaths) throws IOException {
    super.appendData(normalizePath(keyPath), normalizePaths(boundPaths));
  }

  @Nullable
  @Override
  public Collection<String> getState(@NotNull String keyPath) throws IOException {
    Collection<String> collection = super.getState(normalizePath(keyPath));
    return collection != null ? map(collection, toFull()) : null;
  }

  @Override
  public void remove(@NotNull String keyPath) throws IOException {
    super.remove(normalizePath(keyPath));
  }

  @Override
  public Collection<String> getKeys() throws IOException {
    Collection<String> keys = super.getKeys();
    return keys != null ? map(keys, toFull()) : null;
  }

  @Override
  public Iterator<String> getKeysIterator() throws IOException {
    return JBIterator.from(super.getKeysIterator()).map(toFull());
  }

  public final void removeData(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    final Collection<String> outputPaths = getState(keyPath);
    if (outputPaths != null) {
      final boolean removed = outputPaths.remove(normalizePath(boundPath));
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
      final Set<String> result = new ObjectLinkedOpenHashSet<>(FileUtil.PATH_HASHING_STRATEGY);
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        final String str = IOUtil.readUTF(stream);
        result.add(str);
      }
      return result;
    }
  }

  @NotNull
  private Function<String, String> toFull() {
    return s -> myRelativizer.toFull(s);
  }

  private String normalizePath(@NotNull String path) {
    return myRelativizer.toRelative(path);
  }

  private Collection<String> normalizePaths(Collection<String> outputs) {
    Collection<String> normalized = new ArrayList<>(outputs.size());
    for (String out : outputs) {
      normalized.add(normalizePath(out));
    }
    return normalized;
  }

}
