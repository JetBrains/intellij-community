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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TerminateRemoteProcessDialog {
  public static @Nullable ProcessCloseConfirmation show(Project project,
                                                        @NotNull String sessionName,
                                                        @NotNull ProcessHandler processHandler) {
    //noinspection deprecation
    if (processHandler.isSilentlyDestroyOnClose() ||
        Boolean.TRUE.equals(processHandler.getUserData(ProcessHandler.SILENTLY_DESTROY_ON_CLOSE))) {
      return ProcessCloseConfirmation.TERMINATE;
    }

    boolean canDisconnect =
      !Boolean.TRUE.equals(processHandler.getUserData(RunContentManagerImpl.ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY));
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
    processHandler.addProcessListener(listener);

    boolean defaultDisconnect = processHandler.detachIsDefault();
    int exitCode = Messages.showDialog(project,
                                       ExecutionBundle.message("terminate.process.confirmation.text", sessionName),
                                       ExecutionBundle.message("process.is.running.dialog.title", sessionName),
                                       ArrayUtil.toStringArray(options),
                                       canDisconnect && defaultDisconnect ? 1 : 0,
                                       Messages.getWarningIcon(),
                                       doNotAskOption);
    processHandler.removeProcessListener(listener);
    if (alreadyGone.get()) {
      return ProcessCloseConfirmation.DISCONNECT;
    }

    return getConfirmation(exitCode, canDisconnect);
  }

  private static ProcessCloseConfirmation getConfirmation(int button, boolean withDisconnect) {
    if (button == 0) return ProcessCloseConfirmation.TERMINATE;
    if (button == 1 && withDisconnect) return ProcessCloseConfirmation.DISCONNECT;
    return null;
  }
}
