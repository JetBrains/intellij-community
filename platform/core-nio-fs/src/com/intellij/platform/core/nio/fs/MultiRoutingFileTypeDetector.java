// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;

public final class MultiRoutingFileTypeDetector extends FileTypeDetector {

  @NotNull FileTypeDetector detector = MultiRoutingFileSystemProvider.getFileTypeDetector(FileSystems.getDefault().provider());

  @Override
  public String probeContentType(Path path) throws IOException {
    return detector.probeContentType(path);
  }
}
