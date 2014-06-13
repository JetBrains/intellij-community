/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * created at Dec 14, 2001
 * @author Jeka
 */
package com.intellij.execution;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class TerminateRemoteProcessDialog {
  public static int show(final Project project,
                         final String sessionName,
                         final TerminateOption option) {
    final String message = option.myAlwaysUseDefault && !option.myDetach ?
                           ExecutionBundle.message("terminate.process.confirmation.text", sessionName) :
                           ExecutionBundle.message("disconnect.process.confirmation.text", sessionName);
    final String okButtonText = option.myAlwaysUseDefault && !option.myDetach ?
                                ExecutionBundle.message("button.terminate") :
                                ExecutionBundle.message("button.disconnect");
    final String[] options = new String[] {okButtonText, CommonBundle.getCancelButtonText()};
    return Messages.showDialog(project, message, ExecutionBundle.message("process.is.running.dialog.title", sessionName),
                        options, 0, Messages.getWarningIcon(),
                        option);
  }

  public static class TerminateOption implements DialogWrapper.DoNotAskOption {
    private boolean myDetach;
    private final boolean myAlwaysUseDefault;

    public TerminateOption(boolean detachIsDefault, boolean alwaysUseDefault) {
      myDetach = detachIsDefault;
      myAlwaysUseDefault = alwaysUseDefault;
    }

    @Override
    public boolean isToBeShown() {
      return myDetach;
    }

    @Override
    public void setToBeShown(boolean value, int exitCode) {
      myDetach = value;
    }

    @Override
    public boolean canBeHidden() {
      return !myAlwaysUseDefault;
    }

    @Override
    public boolean shouldSaveOptionsOnCancel() {
      return false;
    }

    @NotNull
    @Override
    public String getDoNotShowMessage() {
      return ExecutionBundle.message("terminate.after.disconnect.checkbox");
    }
  }
}
