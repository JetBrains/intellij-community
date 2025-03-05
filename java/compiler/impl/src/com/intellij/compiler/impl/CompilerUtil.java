// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.PathUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;

public final class CompilerUtil {
  private static final Logger LOG = Logger.getInstance(CompilerUtil.class);

  public static void refreshIOFiles(final @NotNull Collection<? extends File> files) {
    if (!files.isEmpty()) {
      LocalFileSystem.getInstance().refreshIoFiles(files);
    }
  }

  /**
   * A lightweight procedure which ensures that given roots exist in the VFS.
   * No actual refresh is performed.
   */
  public static void refreshOutputRoots(@NotNull Collection<String> outputRoots) {
    LocalFileSystem fs = LocalFileSystem.getInstance();
    Collection<VirtualFile> toRefresh = new HashSet<>();

    for (String outputRoot : outputRoots) {
      FileAttributes attributes = FileSystemUtil.getAttributes(FileUtil.toSystemDependentName(outputRoot));
      VirtualFile vFile = fs.findFileByPath(outputRoot);
      if (attributes != null && vFile == null) {
        VirtualFile parent = fs.refreshAndFindFileByPath(PathUtil.getParentPath(outputRoot));
        if (parent != null && toRefresh.add(parent)) {
          parent.getChildren();
        }
      }
      else if (attributes == null && vFile != null ||
               attributes != null && attributes.isDirectory() != vFile.isDirectory()) {
        toRefresh.add(vFile);
      }
    }

    if (!toRefresh.isEmpty()) {
      RefreshQueue.getInstance().refresh(false, false, null, toRefresh);
    }
  }

  public static <T extends Throwable> void runInContext(CompileContext context, @NlsContexts.ProgressText String title, ThrowableRunnable<T> action) throws T {
    ProgressIndicator indicator = context.getProgressIndicator();
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

  public static void logDuration(final String activityName, long duration) {
    LOG.info(activityName + " took " + duration + " ms: " + duration / 60000 + " min " + (duration % 60000) / 1000 + "sec");
  }
}