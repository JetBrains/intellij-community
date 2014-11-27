package com.intellij.openapi.util.diff.contents;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public interface BinaryFileContent extends DiffContent, FileContent {
  /**
   * @return Binary representation of content.
   */
  @NotNull
  byte[] getBytes() throws IOException;
}
