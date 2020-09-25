// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdk;
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix;
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask;
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.function.Function;

@ApiStatus.Internal
public final class UnknownSdkDownloader {
  private static final Logger LOG = Logger.getInstance(UnknownSdkDownloader.class);

  @ApiStatus.Internal
  public static void downloadFix(@Nullable Project project,
                                 @NotNull UnknownSdk info,
                                 @NotNull UnknownSdkDownloadableSdkFix fix,
                                 @NotNull Function<? super SdkDownloadTask, ? extends Sdk> createSdk,
                                 @NotNull Consumer<? super Sdk> onSdkNameReady,
                                 @NotNull Consumer<? super Sdk> onCompleted) {
    if (!Registry.is("unknown.sdk.apply.download.fix")) {
      ApplicationManager.getApplication().invokeLater(() -> {
        onCompleted.consume(null);
      });
      return;
    }

    String title = ProjectBundle.message("progress.title.downloading.sdk");
    new Task.Backgroundable(project, title, true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Sdk sdk = null;
        try {
          SdkDownloadTask task = fix.createTask(indicator);
          SdkDownloadTracker downloadTracker = SdkDownloadTracker.getInstance();

          sdk = createSdk.apply(task);
          downloadTracker.configureSdk(sdk, task);
          onSdkNameReady.consume(sdk);
          downloadTracker.downloadSdk(task, Collections.singletonList(sdk), indicator);
        }
        catch (Exception error) {
          sdk = null;
          if (error instanceof ControlFlowException) ExceptionUtil.rethrow(error);
          if (indicator.isCanceled()) return;

          LOG.warn("Failed to download " +
                   info.getSdkType().getPresentableName() +
                   " " +
                   fix.getDownloadDescription() +
                   " for " +
                   info +
                   ". " +
                   error.getMessage(), error);

          ApplicationManager.getApplication().invokeLater(() -> {
            Messages.showErrorDialog(
              ProjectBundle.message("dialog.message.failed.to.download.0.1", fix.getDownloadDescription(), error.getMessage()), title);
          });
        } finally {
          onCompleted.consume(sdk);
        }
      }
    }.queue();
  }
}
