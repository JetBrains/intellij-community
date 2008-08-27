package com.intellij.openapi.fileTypes;

import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public interface FileTypeConsumer {
  void consume(final FileType fileType, @NonNls final String extensions);

  void consume(final FileType fileType, final FileNameMatcher... matchers);
}
