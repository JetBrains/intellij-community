/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Danila Ponomarenko
 */
public abstract class BaseElementAtCaretIntentionAction extends BaseIntentionAction {
  private volatile boolean useElementToTheLeft;

  @Override
  public final boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!checkFile(file)) return false;

    useElementToTheLeft = false;
    int offset = editor.getCaretModel().getOffset();
    PsiElement elementToTheRight = file.findElementAt(offset);
    if (elementToTheRight != null && isAvailable(project, editor, elementToTheRight)) {
      return true;
    }

    PsiElement elementToTheLeft = offset > 0 ? file.findElementAt(offset - 1) : null;
    if (elementToTheLeft != null && isAvailable(project, editor, elementToTheLeft)) {
      useElementToTheLeft = true;
      return true;
    }

    return false;
  }

  protected boolean checkFile(@NotNull PsiFile file) {
    return canModify(file);
  }

  /**
   * Checks whether this intention is available at a caret offset in file.
   * If this method returns {@code true}, a light bulb for this intention is shown.
   *
   * @param project the project in which the availability is checked.
   * @param editor  the editor in which the intention will be invoked.
   * @param element the element under caret.
   * @return true if the intention is available, false otherwise.
   */
  public abstract boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element);

  @Override
  public final void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(useElementToTheLeft ? offset - 1 : offset);
    if (element == null) {
      return;
    }

    invoke(project, editor, element);
  }

  /**
   * Invokes intention action for the element under cursor.
   *
   * @param project the project in which the file is opened.
   * @param editor  the editor for the file.
   * @param element the element under cursor.
   * @throws IncorrectOperationException On errors.
   */
  public abstract void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException;
}