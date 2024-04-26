// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
 */
public final class SourceToOutputMappingImpl implements SourceToOutputMapping, StorageOwner {
  private final OneToManyPathsMapping myMapping;

  public SourceToOutputMappingImpl(File storePath, PathRelativizerService relativizer) throws IOException {
    myMapping = new OneToManyPathsMapping(storePath, relativizer);
  }

  @Override
  public void setOutputs(@NotNull String srcPath, @NotNull Collection<String> outputs) throws IOException {
    myMapping.update(srcPath, outputs);
  }

  @Override
  public void setOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
    myMapping.update(srcPath, outputPath);
  }

  @Override
  public void appendOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
    myMapping.appendData(srcPath, outputPath);
  }

  @Override
  public void remove(@NotNull String srcPath) throws IOException {
    myMapping.remove(srcPath);
  }

  @Override
  public void removeOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
    myMapping.removeData(srcPath, outputPath);
  }

  @Override
  public @NotNull Collection<String> getSources() throws IOException {
    return myMapping.getKeys();
  }

  @Override
  public @Nullable Collection<String> getOutputs(@NotNull String srcPath) throws IOException {
    return myMapping.getState(srcPath);
  }

  @Override
  public @NotNull Iterator<String> getOutputsIterator(@NotNull String srcPath) throws IOException {
    return myMapping.getStateIterator(srcPath);
  }

  @Override
  public @NotNull Iterator<String> getSourcesIterator() throws IOException {
    return myMapping.getKeysIterator();
  }

  @Override
  public void flush(boolean memoryCachesOnly) {
    myMapping.flush(memoryCachesOnly);
  }

  @Override
  public void close() throws IOException {
    myMapping.close();
  }

  @Override
  public void clean() throws IOException {
    myMapping.clean();
  }
}
