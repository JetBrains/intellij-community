// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;

public final class CompilerUtil {
  private static final Logger LOG = Logger.getInstance(CompilerUtil.class);

  /** @deprecated trivial; prefer {@link LocalFileSystem#refreshNioFiles} */
  @Deprecated(forRemoval = true)
  @SuppressWarnings({"UnnecessaryFullyQualifiedName", "IO_FILE_USAGE"})
  public static void refreshIOFiles(@NotNull Collection<? extends java.io.File> files) {
    if (!files.isEmpty()) {
      LocalFileSystem.getInstance().refreshIoFiles(files);
    }
  }

  /**
   * A lightweight procedure which ensures that given roots exist in the VFS.
   * No recursive refresh is performed.
   */
  public static void refreshOutputRoots(@NotNull Collection<String> outputRoots) {
    var fs = LocalFileSystem.getInstance();
    var toRefresh = new HashSet<VirtualFile>();

    for (var outputRoot : outputRoots) {
      var outputPath = (Path)null;
      var attributes = (BasicFileAttributes)null;
      try {
        outputPath = Path.of(outputRoot);
        attributes = Files.readAttributes(outputPath, BasicFileAttributes.class);
      }
      catch (NoSuchFileException e) {
        LOG.debug(e);
      }
      catch (IOException | InvalidPathException e) {
        LOG.info(e.getClass().getName() + ": " + e.getMessage());
        LOG.debug(e);
      }

      var vFile = fs.findFileByPath(outputRoot);
      if (attributes != null && vFile == null) {
        var parent = fs.refreshAndFindFileByNioFile(outputPath.getParent());
        if (parent != null && toRefresh.add(parent)) {
          parent.getChildren();
        }
      }
      else if (
        attributes == null && vFile != null ||
        attributes != null && attributes.isDirectory() != vFile.isDirectory()
      ) {
        toRefresh.add(vFile);
      }
    }

    if (!toRefresh.isEmpty()) {
      RefreshQueue.getInstance().refresh(false, false, null, toRefresh);
    }
  }

  public static <T extends Throwable> void runInContext(
    @NotNull CompileContext context,
    @Nullable @NlsContexts.ProgressText String title,
    @NotNull ThrowableRunnable<T> action
  ) throws T {
    var indicator = context.getProgressIndicator();
    if (title != null) {
      indicator.pushState();
      indicator.setText(title);
    }
    try {
      action.run();
    }
    finally {
      if (title != null) {
        indicator.popState();
      }
    }
  }

  public static void logDuration(String activityName, long duration) {
    LOG.info(activityName + " took " + duration + " ms: " + duration / 60000 + " min " + (duration % 60000) / 1000 + "sec");
  }
}
