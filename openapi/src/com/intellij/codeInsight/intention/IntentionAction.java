/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
   * Returns this intention's "id". This id is used to externalize "auto-show"
   * state of intentions. When user clicks on a lightbulb in intention list),
   * all intentions with the same family name gets enabled/disabled.
   * @return
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
