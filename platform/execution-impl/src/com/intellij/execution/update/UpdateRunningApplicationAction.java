// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.update;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class UpdateRunningApplicationAction extends AnAction {
  UpdateRunningApplicationAction() {
    super(ExecutionBundle.messagePointer("action.AnAction.text.update.running.application"),
          ExecutionBundle.messagePointer("action.AnAction.description.update.running.application"),
          AllIcons.Javaee.UpdateRunningApplication);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    final RunContentDescriptor contentDescriptor = e.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
    final Presentation presentation = e.getPresentation();
    if (contentDescriptor != null && project != null) {
      final ProcessHandler processHandler = contentDescriptor.getProcessHandler();
      final RunningApplicationUpdater updater = findUpdater(project, processHandler);
      if (updater != null) {
        presentation.setEnabled(processHandler.isStartNotified() && !processHandler.isProcessTerminating()
                                && !processHandler.isProcessTerminated());
        presentation.setText(updater.getDescription());
      }
      else {
        presentation.setEnabled(false);
      }
      presentation.setVisible(true);
      return;
    }

    final List<RunningApplicationUpdater> updaters = getAvailableUpdaters(project);
    final boolean enable = !updaters.isEmpty();
    presentation.setEnabledAndVisible(enable);
    if (updaters.size() == 1) {
      presentation.setText(updaters.get(0).getDescription());
    }
    else {
      presentation.setText(ExecutionBundle.messagePointer("action.presentation.UpdateRunningApplicationAction.text"));
    }
  }

  @Nullable
  private static RunningApplicationUpdater findUpdater(@NotNull Project project, @Nullable ProcessHandler processHandler) {
    if (processHandler == null) return null;

    for (RunningApplicationUpdaterProvider provider : RunningApplicationUpdaterProvider.EP_NAME.getExtensions()) {
      final RunningApplicationUpdater updater = provider.createUpdater(project, processHandler);
      if (updater != null) {
        return updater;
      }
    }
    return null;
  }

  private static List<RunningApplicationUpdater> getAvailableUpdaters(@Nullable Project project) {
    if (project == null) {
      return Collections.emptyList();
    }

    List<RunningApplicationUpdater> result = new ArrayList<>();
    ProcessHandler[] processes = ExecutionManager.getInstance(project).getRunningProcesses();
    for (ProcessHandler process : processes) {
      if (!process.isProcessTerminated() && !process.isProcessTerminating() && process.isStartNotified()) {
        ContainerUtil.addIfNotNull(result, findUpdater(project, process));
      }
    }
    return result;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;

    final RunContentDescriptor contentDescriptor = e.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
    if (contentDescriptor != null) {
      final RunningApplicationUpdater updater = findUpdater(project, contentDescriptor.getProcessHandler());

      if (updater != null) {
        updater.performUpdate(e);
        return;
      }
    }

    final List<RunningApplicationUpdater> updaters = getAvailableUpdaters(project);
    if (updaters.isEmpty()) return;

    if (updaters.size() > 1) {
      final ListPopup popup =
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(
          ExecutionBundle.message("popup.title.select.process.to.update"), updaters) {
          @NotNull
          @Override
          public String getTextFor(RunningApplicationUpdater value) {
            return value.getShortName();
          }

          @Override
          public Icon getIconFor(RunningApplicationUpdater value) {
            return value.getIcon();
          }

          @Override
          public PopupStep onChosen(final RunningApplicationUpdater selectedValue, boolean finalChoice) {
            return doFinalStep(() -> selectedValue.performUpdate(e));
          }
        });
      popup.showCenteredInCurrentWindow(project);
    }
    else {
      updaters.get(0).performUpdate(e);
    }
  }
}
