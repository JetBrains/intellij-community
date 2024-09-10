// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.javac.Iterators;

import java.io.*;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class OneToManyPathsMapping extends AbstractStateStorage<String, Collection<String>> {
  private final PathRelativizerService relativizer;

  public OneToManyPathsMapping(File storePath, PathRelativizerService relativizer) throws IOException {
    super(storePath, PathStringDescriptors.createPathStringDescriptor(), new PathCollectionExternalizer());
    this.relativizer = relativizer;
  }

  @Override
  public void update(@NotNull String keyPath, @SuppressWarnings("NullableProblems") @NotNull Collection<String> boundPaths) throws IOException {
    super.update(normalizePath(keyPath), normalizePaths((List<String>)boundPaths));
  }

  public void update(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    super.update(normalizePath(keyPath), List.of(normalizePath(boundPath)));
  }

  public void appendData(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    super.appendData(normalizePath(keyPath), List.of(normalizePath(boundPath)));
  }

  @Override
  public void appendData(@NotNull String keyPath, @NotNull Collection<String> boundPaths) throws IOException {
    super.appendData(normalizePath(keyPath), normalizePaths((List<String>)boundPaths));
  }

  @Override
  public @Nullable Collection<String> getState(@NotNull String keyPath) throws IOException {
    List<String> collection = (List<String>)super.getState(relativizer.toRelative(keyPath));
    if (collection == null || collection.isEmpty()) {
      return null;
    }
    else {
      String[] result = new String[collection.size()];
      for (int i = 0, size = collection.size(); i < size; i++) {
        result[i] = relativizer.toFull(collection.get(i));
      }
      return Arrays.asList(result);
    }
  }

  public @NotNull Iterator<String> getStateIterator(@NotNull String keyPath) throws IOException {
    List<String> collection = (List<String>)super.getState(relativizer.toRelative(keyPath));
    return collection == null ? Collections.emptyIterator() : Iterators.map(collection.iterator(), relativizer::toFull);
  }

  @Override
  public void remove(@NotNull String keyPath) throws IOException {
    super.remove(relativizer.toRelative(keyPath));
  }

  @Override
  public @NotNull List<String> getKeys() throws IOException {
    List<String> collection = (List<String>)super.getKeys();
    if (collection.isEmpty()) {
      return List.of();
    }

    String[] result = new String[collection.size()];
    for (int i = 0; i < collection.size(); i++) {
      result[i] = relativizer.toFull(collection.get(i));
    }
    return Arrays.asList(result);
  }

  @Override
  public Iterator<String> getKeysIterator() throws IOException {
    return Iterators.map(super.getKeysIterator(), relativizer::toFull);
  }

  public void removeData(@NotNull String keyPath, @NotNull String boundPath) throws IOException {
    String relativeKeyPath = relativizer.toRelative(keyPath);
    List<String> oldList = (List<String>)super.getState(relativeKeyPath);
    if (oldList == null || oldList.isEmpty()) {
      return;
    }

    String relativePath = relativizer.toRelative(boundPath);
    int index = oldList.indexOf(relativePath);
    if (index < 0) {
      return;
    }

    if (oldList.size() == 1) {
      super.remove(relativeKeyPath);
      return;
    }

    List<String> newState = new ArrayList<>(oldList.size() - 1);
    for (int i = 0; i < oldList.size(); i++) {
      if (index != i) {
        newState.add(oldList.get(i));
      }
    }
    super.update(relativeKeyPath, newState);
  }

  private static final class PathCollectionExternalizer implements DataExternalizer<Collection<String>> {
    @Override
    public void save(@NotNull DataOutput out, Collection<String> value) throws IOException {
      for (String str : value) {
        IOUtil.writeUTF(out, str);
      }
    }

    @Override
    public List<String> read(@NotNull DataInput in) throws IOException {
      List<String> result = new ArrayList<>();
      DataInputStream stream = (DataInputStream)in;
      HashSet<String> guard = new HashSet<>();
      while (stream.available() > 0) {
        String s = IOUtil.readUTF(stream);
        if (guard.add(s)) {
          result.add(s);
        }
      }
      return result;
    }
  }

  private String normalizePath(@NotNull String path) {
    return relativizer.toRelative(path);
  }

  private @NotNull @Unmodifiable List<String> normalizePaths(@NotNull List<String> outputs) {
    String[] normalized = new String[outputs.size()];
    for (int i = 0; i < normalized.length; i++) {
      normalized[i] = relativizer.toRelative(outputs.get(i));
    }
    return Arrays.asList(normalized);
  }
}
