/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * To solve "caret after last symbol" problem consider using {@link com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction}
 *
 * @author Anna Kozlova
 * @author Konstantin Bulenkov
 */
public abstract class PsiElementBaseIntentionAction extends BaseIntentionAction {
  @Override
  public final void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!file.getManager().isInProject(file)) return;
    final PsiElement element = getElement(editor, file);
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
   * @throws com.intellij.util.IncorrectOperationException
   *
   */
  public abstract void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException;

  @Override
  public final boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (file == null) return false;
    final PsiManager manager = file.getManager();
    if (manager == null) return false;
    if (!manager.isInProject(file)) return false;
    final PsiElement element = getElement(editor, file);
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

  @Nullable
  private static PsiElement getElement(@NotNull Editor editor, @NotNull PsiFile file) {
    CaretModel caretModel = editor.getCaretModel();
    int position = caretModel.getOffset();
    return file.findElementAt(position);
  }
}