/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class SuppressIntentionAction implements Iconable, IntentionAction {
  private String myText = "";
  public static SuppressIntentionAction[] EMPTY_ARRAY = new SuppressIntentionAction[0];

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.General.InspectionsOff;
  }

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  protected void setText(@NotNull String text) {
    myText = text;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public String toString() {
    return getText();
  }

  @Override
  public final void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = getElement(editor, file);
    if (element != null) {
      invoke(project, editor, element);
    }
  }

  /**
   * Invokes intention action for the element under caret.
   *
   * @param project the project in which the file is opened.
   * @param editor  the editor for the file.
   * @param element the element under cursor.
   *
   */
  public abstract void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException;

  @Override
  public final boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (file == null || editor == null) return false;
    PsiElement element = getElement(editor, file);
    return element != null && isAvailable(project, editor, element);
  }

  /**
   * Checks whether this intention is available at a caret offset in file.
   * If this method returns true, a light bulb for this intention is shown.
   *
   * @param project the project in which the availability is checked.
   * @param editor  the editor in which the intention will be invoked.
   * @param element the element under caret.
   * @return true if the intention is available, false otherwise.
   */
  public abstract boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element);

  public boolean isSuppressAll() {
    return false;
  }

  @Nullable
  private static PsiElement getElement(@NotNull Editor editor, @NotNull PsiFile file) {
    CaretModel caretModel = editor.getCaretModel();
    int position = caretModel.getOffset();
    return file.findElementAt(position);
  }
}
