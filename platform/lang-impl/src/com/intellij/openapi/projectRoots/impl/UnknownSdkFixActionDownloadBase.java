// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;

public abstract class UnknownSdkFixActionDownloadBase extends UnknownSdkFixActionBase {

  public enum DownloadFixReaction {
    SHOW_POPUP {
      @Override
      public String toString() { return ProjectBundle.message("advanced.setting.sdk.configuration.install.request.ask"); }
    },
    ALWAYS_ACCEPT {
      @Override
      public String toString() { return ProjectBundle.message("advanced.setting.sdk.configuration.install.request.accept"); }
    },
    ALWAYS_REJECT {
      @Override
      public String toString() { return ProjectBundle.message("advanced.setting.sdk.configuration.install.request.deny"); }
    }
  }

  private static final Logger LOG = Logger.getInstance(UnknownSdkFixActionDownloadBase.class);

  @NotNull
  protected abstract UnknownSdkDownloadTask createTask();

  @NotNull
  protected abstract String getDownloadDescription();

  @Nullable
  protected String getSdkLookupReason() { return null; }

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
    final var reactionSetting = AdvancedSettings.getEnum("sdk.configuration.install.request", DownloadFixReaction.class);

    if (hasConsent() || reactionSetting == DownloadFixReaction.ALWAYS_ACCEPT
        || Registry.is("sdk.download.consent", false)
        || ApplicationManager.getApplication().isUnitTestMode()) {
      return createTask().withListener(getMulticaster());
    }

    if (reactionSetting == DownloadFixReaction.ALWAYS_REJECT) {
      getMulticaster().onResolveCancelled();
      return null;
    }

    collectConsent(project);

    if (hasConsent()) {
      return createTask().withListener(getMulticaster());
    } else {
      getMulticaster().onResolveCancelled();
      return null;
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
      .yesText(ProjectBundle.message("dialog.sdk.download.yes"))
      .doNotAsk(new DoNotAskOption() {
        @Override public boolean isToBeShown() { return true; }
        @Override public boolean canBeHidden() { return true; }
        @Override public boolean shouldSaveOptionsOnCancel() { return true; }

        @Override
        public void setToBeShown(boolean value, int exitCode) {
          if (value) return;
          AdvancedSettings.setEnum(
            "sdk.configuration.install.request",
            exitCode == DialogWrapper.OK_EXIT_CODE ? DownloadFixReaction.ALWAYS_ACCEPT : DownloadFixReaction.ALWAYS_REJECT
          );
        }

        @NotNull
        @Override
        public String getDoNotShowMessage() {
          return ProjectBundle.message("dialog.sdk.download.do.not.ask");
        }
      });

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

  @NotNull
  @Override
  public final Sdk applySuggestionBlocking(@NotNull ProgressIndicator indicator) {
    return createTask().withListener(getMulticaster()).runBlocking(indicator);
  }
}
