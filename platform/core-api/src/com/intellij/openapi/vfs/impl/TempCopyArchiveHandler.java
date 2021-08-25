// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

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
    FileUtil.delete(getTempFile());
  }

  protected @NotNull File getTempCopy(@NotNull ThrowableConsumer<? super File, ? extends IOException> initializer) throws IOException {
    File file = getFile();
    File copy = getTempFile();
    if (!copy.exists() || copy.lastModified() != file.lastModified()) {
      FileUtil.createParentDirs(copy);
      initializer.consume(copy);
      Files.setLastModifiedTime(copy.toPath(), Files.getLastModifiedTime(file.toPath()));
    }
    return copy;
  }

  private @NotNull File getTempFile() {
    File file = getFile();
    String hash = Integer.toHexString(file.getPath().hashCode());
    return new File(PathManager.getSystemPath() + '/' + getTempDir() + '/' + file.getName() + '_' + hash);
  }
}
