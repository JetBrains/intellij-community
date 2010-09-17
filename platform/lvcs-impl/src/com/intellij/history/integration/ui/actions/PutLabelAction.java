/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.integration.ui.actions;

import com.intellij.history.LocalHistory;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.openapi.vfs.VirtualFile;

import static com.intellij.history.integration.LocalHistoryBundle.message;

public class PutLabelAction extends LocalHistoryActionWithDialog {
  @Override
  protected void showDialog(Project p, IdeaGateway gw, VirtualFile f, AnActionEvent e) {
    String labelName = Messages.showInputDialog(p, message("put.label.name"), message("put.label.dialog.title"),null,
                                                "", new NonEmptyInputValidator());
    if (labelName == null) return;
    LocalHistory.getInstance().putUserLabel(p, labelName);
  }
}