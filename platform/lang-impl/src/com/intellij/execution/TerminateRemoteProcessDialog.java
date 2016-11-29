/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.execution;

import com.intellij.CommonBundle;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

public class TerminateRemoteProcessDialog {
  public static GeneralSettings.ProcessCloseConfirmation show(final Project project,
                         final String sessionName,
                         boolean canDisconnect,
                         boolean defaultDisconnect) {
    GeneralSettings.ProcessCloseConfirmation confirmation = GeneralSettings.getInstance().getProcessCloseConfirmation();
    if (confirmation != GeneralSettings.ProcessCloseConfirmation.ASK) {
      if (confirmation == GeneralSettings.ProcessCloseConfirmation.DISCONNECT && !canDisconnect) {
        confirmation = GeneralSettings.ProcessCloseConfirmation.TERMINATE;
      }
      return confirmation;
    }
    List<String> options = new ArrayList<>(3);
    options.add(ExecutionBundle.message("button.terminate"));
    if (canDisconnect) {
      options.add(ExecutionBundle.message("button.disconnect"));
    }
    options.add(CommonBundle.getCancelButtonText());
    DialogWrapper.DoNotAskOption.Adapter doNotAskOption = new DialogWrapper.DoNotAskOption.Adapter() {
      @Override
      public void rememberChoice(boolean isSelected, int exitCode) {
        if (isSelected) {
          GeneralSettings.ProcessCloseConfirmation confirmation = getConfirmation(exitCode, canDisconnect);
          if (confirmation != null) {
            GeneralSettings.getInstance().setProcessCloseConfirmation(confirmation);
          }
        }
      }
    };
    return getConfirmation(Messages.showDialog(project,
                               ExecutionBundle.message("terminate.process.confirmation.text", sessionName),
                               ExecutionBundle.message("process.is.running.dialog.title", sessionName),
                               ArrayUtil.toStringArray(options),
                               canDisconnect && defaultDisconnect ? 1 : 0,
                               Messages.getWarningIcon(),
                               doNotAskOption), canDisconnect);
  }

  private static GeneralSettings.ProcessCloseConfirmation getConfirmation(int button, boolean withDisconnect) {
    switch (button) {
      case 0:
        return GeneralSettings.ProcessCloseConfirmation.TERMINATE;
      case 1:
        if (withDisconnect) {
          return GeneralSettings.ProcessCloseConfirmation.DISCONNECT;
        }
      default:
          return null;
    }
  }
}
