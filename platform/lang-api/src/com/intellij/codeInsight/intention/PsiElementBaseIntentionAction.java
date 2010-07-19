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

package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Anna Kozlova
 * @author Konstantin Bulenkov
 */
public abstract class PsiElementBaseIntentionAction extends BaseIntentionAction {
  /**
   * Invokes intention action for the element under cursor
   *
   * @param project the project in which the file is opened.
   * @param editor the editor for the file
   * @param element the element under cursor

   * @throws com.intellij.util.IncorrectOperationException ...
   */
  public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiElement element = getElement(editor, file);
    return element == null ? false : isAvailable(project, editor, element);
  }

  @Nullable
  protected static PsiElement getElement(Editor editor, PsiFile file) {
    if (!file.getManager().isInProject(file)) return null;
    final CaretModel caretModel = editor.getCaretModel();
    final int position = caretModel.getOffset();
    return file.findElementAt(position);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    invoke(project, editor, getElement(editor, file));
  }

  /**
   * Checks whether this intention is available at a caret offset in file.
   * If this method returns true, a light bulb for this intention is shown.
   *
   * @param project the project in which the availability is checked.
   * @param editor the editor in which the intention will be invoked.
   * @param element the element under caret.
   * @return true if the intention is available, false otherwise.
   */
  public abstract boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element);
}