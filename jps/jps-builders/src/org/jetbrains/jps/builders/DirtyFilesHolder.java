package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;

/**
 * @author nik
 */
public interface DirtyFilesHolder<R extends BuildRootDescriptor, T extends BuildTarget<R>> {
  void processDirtyFiles(@NotNull FileProcessor<R, T> processor) throws IOException;

  boolean hasRemovedFiles();

  @NotNull
  Collection<String> getRemovedFiles(@NotNull T target);
}
