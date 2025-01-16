// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.storage;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.incremental.storage.SourceToOutputMappingCursor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public interface SourceToOutputMapping {
  void setOutputs(@NotNull Path sourceFile, @NotNull List<@NotNull Path> outputs) throws IOException;

  void appendOutput(@NotNull String sourcePath, @NotNull String outputPath) throws IOException;

  void remove(@NotNull Path sourceFile) throws IOException;

  /**
   * @deprecated Use {@link #remove(Path)}
   */
  @Deprecated
  default void remove(@NotNull String sourcePath) throws IOException {
    remove(Path.of(sourcePath));
  }

  void removeOutput(@NotNull String sourcePath, @NotNull String outputPath) throws IOException;

  /**
   * @deprecated Use {@link #getSourcesIterator()}
   */
  @Deprecated(forRemoval = true)
  default @NotNull Collection<@NotNull String> getSources() throws IOException {
    List<String> result = new ArrayList<>();
    Iterator<String> iterator = getSourcesIterator();
    while (iterator.hasNext()) {
      result.add(iterator.next());
    }
    return result;
  }

  @Nullable
  @Unmodifiable
  Collection<@NotNull String> getOutputs(@NotNull String srcPath) throws IOException;

  @ApiStatus.Experimental
  @ApiStatus.Internal
  @Nullable
  @Unmodifiable
  Collection<@NotNull Path> getOutputs(@NotNull Path sourceFile) throws IOException;

  @ApiStatus.Experimental
  @ApiStatus.Internal
  @NotNull
  Iterator<@NotNull Path> getSourceFileIterator() throws IOException;

  @NotNull
  Iterator<@NotNull String> getSourcesIterator() throws IOException;

  @NotNull
  @ApiStatus.Internal
  SourceToOutputMappingCursor cursor() throws IOException;
}
