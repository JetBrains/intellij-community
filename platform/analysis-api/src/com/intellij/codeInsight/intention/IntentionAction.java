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
package com.intellij.codeInsight.intention;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for intention actions. Intention actions are invoked by pressing
 * Alt-Enter in the code editor at the location where an intention is available,
 * and can be enabled or disabled in the "Intentions" settings dialog.
 * <p/>
 * Implement {@link Iconable Iconable} interface to
 * change icon in intention popup menu.
 * <p/>
 * Implement {@link HighPriorityAction HighPriorityAction} or
 * {@link LowPriorityAction LowPriorityAction} to change ordering.
 * <p/>
 * Can be {@link com.intellij.openapi.project.DumbAware}.
 *
 * @see IntentionManager#registerIntentionAndMetaData(IntentionAction, String...)
 */
public interface IntentionAction {
  IntentionAction[] EMPTY_ARRAY = new IntentionAction[0];
  /**
   * Returns text to be shown in the list of available actions, if this action
   * is available.
   *
   * @see #isAvailable(Project,Editor,PsiFile)
   * @return the text to show in the intention popup.
   */
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull String getText();

  /**
   * Returns the name of the family of intentions. It is used to externalize
   * "auto-show" state of intentions. When user clicks on a lightbulb in intention list,
   * all intentions with the same family name get enabled/disabled.
   * The name is also shown in settings tree.
   *
   * @return the intention family name.
   * @see IntentionManager#registerIntentionAndMetaData(IntentionAction, String...)
   */
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Sentence)
  String getFamilyName();

  /**
   * Checks whether this intention is available at a caret offset in file.
   * If this method returns true, a light bulb for this intention is shown.
   *
   * @param project the project in which the availability is checked.
   * @param editor the editor in which the intention will be invoked.
   * @param file the file open in the editor.
   * @return true if the intention is available, false otherwise.
   */
  boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file);

  /**
   * Called when user invokes intention. This method is called inside command.
   * If {@link #startInWriteAction()} returns true, this method is also called
   * inside write action.
   *
   * @param project the project in which the intention is invoked.
   * @param editor the editor in which the intention is invoked.
   * @param file the file open in the editor.
   */
  void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException;

  /**
   * Indicate whether this action should be invoked inside write action.
   * Should return false if e.g. modal dialog is shown inside the action.
   * If false is returned the action itself is responsible for starting write action
   * when needed, by calling {@link Application#runWriteAction(Runnable)}.
   *
   * @return true if the intention requires a write action, false otherwise.
   */
  boolean startInWriteAction();
}
