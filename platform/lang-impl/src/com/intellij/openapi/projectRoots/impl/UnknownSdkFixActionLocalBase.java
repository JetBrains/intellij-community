// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

public abstract class UnknownSdkFixActionLocalBase extends UnknownSdkFixActionBase {
  protected abstract @NotNull Sdk applyLocalFix();

  protected abstract @NotNull String getSuggestedSdkHome();

  private @NotNull Sdk runWithEvents() {
    ThreadingAssertions.assertEventDispatchThread();

    Listener multicaster = getMulticaster();
    try {
      var sdk = applyLocalFix();
      multicaster.onSdkNameResolved(sdk);
      multicaster.onSdkResolved(sdk);
      return sdk;
    }
    catch (Throwable t) {
      multicaster.onResolveFailed();
      throw t;
    }
  }

  private void refreshVfs() {
    //it may yield empty SDK roots or errors, e.g. EA-239592, IDEA-252376
    Path path = Paths.get(getSuggestedSdkHome());
    VfsUtil.markDirtyAndRefresh(false, true, true, LocalFileSystem.getInstance().findFileByNioFile(path));
  }

  @Override
  public final void applySuggestionAsync(@Nullable Project project) {
    var title = ProjectBundle.message("progress.title.downloading.sdk");
    new Task.Backgroundable(project, title) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          refreshVfs();
          ApplicationManager.getApplication().invokeLater(() -> runWithEvents());
        }
        catch (Throwable t) {
          if (t instanceof ControlFlowException) throw t;

          //must be logged in the applyLocalFix
        }
      }
    }.queue();
  }

  @Override
  public final @NotNull Sdk applySuggestionBlocking(@NotNull ProgressIndicator indicator) {
    refreshVfs();

    var sdk = new AtomicReference<Sdk>(null);
    ApplicationManager.getApplication().invokeAndWait(() -> {
      var r = runWithEvents();
      sdk.set(r);
    });
    return sdk.get();
  }
}
