package com.intellij.openapi.fileTypes;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface FileTypeConsumer {
  String EXTENSION_DELIMITER = ";";

  void consume(final @NotNull FileType fileType, @NonNls final String extensions);

  void consume(final @NotNull FileType fileType, final FileNameMatcher... matchers);

  @Nullable
  FileType getStandardFileTypeByName(@NonNls @NotNull String name);
}
