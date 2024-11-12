// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.NioFiles;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An archive handler which able to copy unpacked data to temporary file
 */
public abstract class TempCopyArchiveHandler extends ArchiveHandler {
  protected TempCopyArchiveHandler(@NotNull String path) {
    super(path);
  }

  protected abstract String getTempDir();

  @Override
  public void clearCaches() {
    super.clearCaches();
    removeTempCopy();
  }

  private void removeTempCopy() {
    try {
      NioFiles.deleteRecursively(getTempFile());
    }
    catch (IOException e) {
      Logger.getInstance(getClass()).error("Failed to delete temp archive " + getTempFile(), e);
    }
  }

  @SuppressWarnings("BoundedWildcard")
  protected @NotNull Path getTempCopy(@NotNull ThrowableComputable<InputStream, IOException> input) throws IOException {
    Path file = getPath(), copy = getTempFile();
    if (!Files.exists(copy) || Files.getLastModifiedTime(copy).toMillis() != Files.getLastModifiedTime(file).toMillis()) {
      NioFiles.createParentDirectories(copy);
      Files.copy(input.compute(), copy);
      Files.setLastModifiedTime(copy, Files.getLastModifiedTime(file));
    }
    return copy;
  }

  private Path getTempFile() {
    Path file = getPath();
    String hash = Integer.toHexString(file.toString().hashCode());
    return PathManager.getSystemDir().resolve(getTempDir()).resolve(file.getFileName() + "_" + hash);
  }
}
