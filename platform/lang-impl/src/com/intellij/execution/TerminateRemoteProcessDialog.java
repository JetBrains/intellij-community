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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

public class TerminateRemoteProcessDialog {
  public static int show(final Project project,
                         final String sessionName,
                         boolean canDisconnect,
                         boolean defaultDisconnect) {
    List<String> options = new ArrayList<>(3);
    options.add(ExecutionBundle.message("button.terminate"));
    if (canDisconnect) {
      options.add(ExecutionBundle.message("button.disconnect"));
    }
    options.add(CommonBundle.getCancelButtonText());
    return Messages.showDialog(project,
                               ExecutionBundle.message("terminate.process.confirmation.text", sessionName),
                               ExecutionBundle.message("process.is.running.dialog.title", sessionName),
                               ArrayUtil.toStringArray(options),
                               canDisconnect && defaultDisconnect ? 1 : 0,
                               Messages.getWarningIcon());
  }
}
