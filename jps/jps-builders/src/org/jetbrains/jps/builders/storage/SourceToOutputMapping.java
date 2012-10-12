package org.jetbrains.jps.builders.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author nik
 */
public interface SourceToOutputMapping {
  void setOutputs(@NotNull String srcPath, @NotNull Collection<String> outputs) throws IOException;

  void setOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException;

  void appendOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException;


  void remove(@NotNull String srcPath) throws IOException;

  void removeOutput(@NotNull String sourcePath, @NotNull String outputPath) throws IOException;


  @NotNull
  Collection<String> getSources() throws IOException;

  @Nullable
  Collection<String> getOutputs(@NotNull String srcPath) throws IOException;

  @NotNull
  Iterator<String> getSourcesIterator() throws IOException;
}
