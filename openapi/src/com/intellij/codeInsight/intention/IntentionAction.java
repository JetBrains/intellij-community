/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

public interface IntentionAction {
  /**
   * Returns text to be shown in the list of available actions, if this action
   * is available.
   * @see #isAvailable(Project,Editor,PsiFile)
   */
  String getText();

  /**
   * @return this intention's "id". This id is used to externalize "auto-show"
   * state of intentions. When user clicks on a lightbulb in intention list),
   * all intentions with the same family name gets enabled/disabled.
   */
  String getFamilyName();

  /**
   * Checks whether this intention is available at a caret offset in file.
   * If this method returns true, a light bulb for this intention is shown.
   */
  boolean isAvailable(Project project, Editor editor, PsiFile file);

  /**
   * Called when user invokes intention. This method is called inside command.
   * If {@link #startInWriteAction()} returns true, this method is also called
   * inside write action.
   */
  void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException;

  /**
   * Indicate whether this action should be invoked inside write action.
   * Should return false if e.g. modal dialog is shown inside the action.
   * If false is returned the action itself is responsible for starting write action
   * when needed
   */
  boolean startInWriteAction();
}
