// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.util.Function;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.javac.Iterators;

import java.io.*;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class OneToManyPathsMapping extends AbstractStateStorage<String, Collection<String>> {
  private final PathRelativizerService myRelativizer;

  public OneToManyPathsMapping(File storePath, PathRelativizerService relativizer) throws IOException {
    super(storePath, PathStringDescriptor.INSTANCE, new PathCollectionExternalizer());
    myRelativizer = relativizer;
  }

  @Override
  public void update(@NotNull String keyPath, @NotNull Collection<String> boundPaths) throws IOException {
    super.update(normalizePath(keyPath), normalizePaths(boundPaths));
  }

  public void update(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    super.update(normalizePath(keyPath), Collections.singleton(normalizePath(boundPath)));
  }

  public void appendData(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    super.appendData(normalizePath(keyPath), Collections.singleton(normalizePath(boundPath)));
  }

  @Override
  public void appendData(@NotNull String keyPath, @NotNull Collection<String> boundPaths) throws IOException {
    super.appendData(normalizePath(keyPath), normalizePaths(boundPaths));
  }

  @Override
  public @Nullable Collection<String> getState(@NotNull String keyPath) throws IOException {
    Collection<String> collection = super.getState(normalizePath(keyPath));
    return collection != null ? ContainerUtil.map(collection, toFull()) : null;
  }

  public @NotNull Iterator<String> getStateIterator(@NotNull String keyPath) throws IOException {
    Collection<String> collection = super.getState(normalizePath(keyPath));
    return collection == null ? Collections.emptyIterator() : Iterators.map(collection.iterator(), myRelativizer::toFull);
  }

  @Override
  public void remove(@NotNull String keyPath) throws IOException {
    super.remove(normalizePath(keyPath));
  }

  @Override
  public Collection<String> getKeys() throws IOException {
    return ContainerUtil.map(super.getKeys(), toFull());
  }

  @Override
  public Iterator<String> getKeysIterator() throws IOException {
    return Iterators.map(super.getKeysIterator(), myRelativizer::toFull);
  }

  public void removeData(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    final Collection<String> outputPaths = getState(keyPath);
    if (outputPaths != null) {
      String normalizedPath = normalizePath(boundPath);
      List<String> newState = ContainerUtil.filter(outputPaths, path -> !normalizedPath.equals(path));
      final boolean removed = newState.size() != outputPaths.size();
      if (removed) {
        if (newState.isEmpty()) {
          remove(keyPath);
        }
        else {
          update(keyPath, newState);
        }
      }
    }
  }

  private static final class PathCollectionExternalizer implements DataExternalizer<Collection<String>> {
    @Override
    public void save(@NotNull DataOutput out, Collection<String> value) throws IOException {
      for (String str : value) {
        IOUtil.writeUTF(out, str);
      }
    }

    @Override
    public Collection<String> read(@NotNull DataInput in) throws IOException {
      final Set<String> result = CollectionFactory.createFilePathLinkedSet();
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        final String str = IOUtil.readUTF(stream);
        result.add(str);
      }
      return result;
    }
  }

  private @NotNull Function<String, String> toFull() {
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
