// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;

public abstract class UnknownSdkFixActionDownloadBase extends UnknownSdkFixActionBase {
  private static final Logger LOG = Logger.getInstance(UnknownSdkFixActionDownloadBase.class);

  protected abstract @NotNull UnknownSdkDownloadTask createTask();

  protected abstract @NotNull String getDownloadDescription();

  protected @Nullable String getSdkLookupReason() { return null; }

  @Override
  public final void applySuggestionAsync(@Nullable Project project) {
    final var task = doCreateTask(project);
    if (task == null) {
      getMulticaster().onResolveCancelled();
    } else {
      task.runAsync(project);
    }
  }

  private UnknownSdkDownloadTask doCreateTask(@Nullable Project project) {
    if (hasConsent()
        || Registry.is("sdk.download.consent", false)
        || ApplicationManager.getApplication().isUnitTestMode()) {
      return createTask().withListener(getMulticaster());
    }

    if (supportsSdkChoice()) {
      doChooseSdk();
    } else {
      collectConsent(project);
    }

    if (hasConsent()) {
      return createTask().withListener(getMulticaster());
    } else {
      getMulticaster().onResolveCancelled();
      return null;
    }
  }

  private void doChooseSdk() {
    try {
      SwingUtilities.invokeAndWait(() -> {
        if (chooseSdk()) {
          giveConsent();
        }
      });
    }
    catch (InterruptedException | InvocationTargetException e) {
      LOG.warn("Failed to get SDK download consent", e);
    }
  }

  private void collectConsent(@Nullable Project project) {
    final String description = ProjectBundle.message("dialog.sdk.download.message", ApplicationInfo.getInstance().getFullApplicationName(), getDownloadDescription());
    final String lookupReason = getSdkLookupReason();

    final var dialog = MessageDialogBuilder
      .yesNo(
        ProjectBundle.message("dialog.sdk.download.title"),
        lookupReason != null ? ProjectBundle.message("dialog.sdk.download.message.with.reason", lookupReason, description) : description
      )
      .yesText(ProjectBundle.message("dialog.sdk.download.yes"));

    try {
      SwingUtilities.invokeAndWait(() -> {
        if (dialog.ask(project)) {
          giveConsent();
        }
      });
    }
    catch (InterruptedException | InvocationTargetException e) {
      LOG.warn("Failed to get SDK download consent", e);
    }
  }

  @Override
  public final @NotNull Sdk applySuggestionBlocking(@NotNull ProgressIndicator indicator) {
    return createTask().withListener(getMulticaster()).runBlocking(indicator);
  }
}
