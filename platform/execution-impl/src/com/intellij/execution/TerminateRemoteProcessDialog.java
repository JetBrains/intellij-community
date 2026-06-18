// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution;

import com.intellij.CommonBundle;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.ProcessCloseConfirmation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TerminateRemoteProcessDialog {
  public static @Nullable ProcessCloseConfirmation show(
    Project project,
    @NotNull String sessionName,
    @NotNull ProcessHandler processHandler
  ) {
    return show(project, List.of(sessionName), List.of(processHandler));
  }

  /**
   * Shows a single confirmation dialog for several running processes at once.
   * {@code sessionNames} and {@code processHandlers} correspond to each other by index and are expected to be of the same size.
   */
  @ApiStatus.Experimental
  public static @Nullable ProcessCloseConfirmation show(
    Project project,
    @NotNull List<@NotNull String> sessionNames,
    @NotNull List<@NotNull ProcessHandler> processHandlers
  ) {
    if (sessionNames.isEmpty() || processHandlers.isEmpty()) {
      throw new IllegalArgumentException("sessionNames and processHandlers must not be empty");
    }
    if (sessionNames.size() != processHandlers.size()) {
      throw new IllegalArgumentException("sessionNames and processHandlers must have the same size");
    }

    if (ContainerUtil.all(processHandlers, TerminateRemoteProcessDialog::isSilentlyDestroyOnClose)) {
      return ProcessCloseConfirmation.TERMINATE;
    }

    boolean canDisconnect = ContainerUtil.all(processHandlers, TerminateRemoteProcessDialog::canDisconnect);
    ProcessCloseConfirmation confirmation = GeneralSettings.getInstance().getProcessCloseConfirmation();
    if (confirmation != ProcessCloseConfirmation.ASK) {
      if (confirmation == ProcessCloseConfirmation.DISCONNECT && !canDisconnect) {
        confirmation = ProcessCloseConfirmation.TERMINATE;
      }
      return confirmation;
    }

    List<String> options = new ArrayList<>(3);
    options.add(ExecutionBundle.message("button.terminate"));
    if (canDisconnect) {
      options.add(ExecutionBundle.message("button.disconnect"));
    }
    options.add(CommonBundle.getCancelButtonText());
    DoNotAskOption.Adapter doNotAskOption = new DoNotAskOption.Adapter() {
      @Override
      public void rememberChoice(boolean isSelected, int exitCode) {
        if (isSelected) {
          ProcessCloseConfirmation confirmation = getConfirmation(exitCode, canDisconnect);
          if (confirmation != null) {
            GeneralSettings.getInstance().setProcessCloseConfirmation(confirmation);
          }
        }
      }
    };

    AtomicBoolean alreadyGone = new AtomicBoolean(false);
    Runnable dialogRemover = Messages.createMessageDialogRemover(project);
    ProcessListener listener = new ProcessListener() {
      @Override
      public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
        alreadyGone.set(true);
        dialogRemover.run();
      }
    };
    for (ProcessHandler processHandler : processHandlers) {
      processHandler.addProcessListener(listener);
    }

    boolean defaultDisconnect = ContainerUtil.all(processHandlers, ProcessHandler::detachIsDefault);
    int exitCode = Messages.showDialog(project,
                                       getMessageText(sessionNames),
                                       getTitleText(sessionNames),
                                       ArrayUtil.toStringArray(options),
                                       canDisconnect && defaultDisconnect ? 1 : 0,
                                       Messages.getWarningIcon(),
                                       doNotAskOption);
    for (ProcessHandler processHandler : processHandlers) {
      processHandler.removeProcessListener(listener);
    }
    if (alreadyGone.get()) {
      return ProcessCloseConfirmation.DISCONNECT;
    }

    return getConfirmation(exitCode, canDisconnect);
  }

  private static boolean isSilentlyDestroyOnClose(@NotNull ProcessHandler processHandler) {
    //noinspection deprecation
    return processHandler.isSilentlyDestroyOnClose() ||
           Boolean.TRUE.equals(processHandler.getUserData(ProcessHandler.SILENTLY_DESTROY_ON_CLOSE));
  }

  private static boolean canDisconnect(@NotNull ProcessHandler processHandler) {
    return !Boolean.TRUE.equals(processHandler.getUserData(RunContentManagerImpl.ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY));
  }

  private static @NlsContexts.DialogTitle String getTitleText(@NotNull List<@NotNull String> sessionNames) {
    if (sessionNames.size() == 1) {
      return ExecutionBundle.message("process.is.running.dialog.title", sessionNames.getFirst());
    }
    return ExecutionBundle.message("processes.are.running.dialog.title");
  }

  private static @NlsContexts.DialogMessage String getMessageText(@NotNull List<@NotNull String> sessionNames) {
    if (sessionNames.size() == 1) {
      return ExecutionBundle.message("terminate.process.confirmation.text", sessionNames.getFirst());
    }
    return ExecutionBundle.message("terminate.processes.confirmation.text", String.join(", ", sessionNames));
  }

  private static ProcessCloseConfirmation getConfirmation(int button, boolean withDisconnect) {
    if (button == 0) return ProcessCloseConfirmation.TERMINATE;
    if (button == 1 && withDisconnect) return ProcessCloseConfirmation.DISCONNECT;
    return null;
  }
}
