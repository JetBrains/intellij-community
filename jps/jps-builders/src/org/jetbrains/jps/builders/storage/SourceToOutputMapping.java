// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.storage;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.storage.SourceToOutputMappingCursor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public interface SourceToOutputMapping {
  void setOutputs(@NotNull String srcPath, @NotNull List<String> outputs) throws IOException;

  void setOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException;

  void appendOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException;

  void remove(@NotNull String srcPath) throws IOException;

  void removeOutput(@NotNull String sourcePath, @NotNull String outputPath) throws IOException;

  /**
   * @deprecated Use {@link #getSourcesIterator()}
   */
  @Deprecated(forRemoval = true)
  default @NotNull Collection<String> getSources() throws IOException {
    List<String> result = new ArrayList<>();
    Iterator<String> iterator = getSourcesIterator();
    while (iterator.hasNext()) {
      result.add(iterator.next());
    }
    return result;
  }

  @Nullable
  Collection<String> getOutputs(@NotNull String srcPath) throws IOException;

  @NotNull
  Iterator<String> getSourcesIterator() throws IOException;

  @NotNull
  @ApiStatus.Internal
  SourceToOutputMappingCursor cursor() throws IOException;
}
