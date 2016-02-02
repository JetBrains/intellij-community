/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

/**
 * @author Danila Ponomarenko
 */
public abstract class BaseElementAtCaretIntentionAction extends BaseIntentionAction {
  private volatile boolean useElementToTheLeft = false;

  @Override
  public final boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!checkFile(file)) return false;

    useElementToTheLeft = false;
    final PsiElement elementToTheRight = getElementToTheRight(editor, file);
    if (elementToTheRight != null && isAvailable(project, editor, elementToTheRight)) {
      return true;
    }

    final PsiElement elementToTheLeft = getElementToTheLeft(editor, file);
    if (elementToTheLeft != null && isAvailable(project, editor, elementToTheLeft)) {
      useElementToTheLeft = true;
      return true;
    }

    return false;
  }

  protected boolean checkFile(@NotNull PsiFile file) {
    return file.getManager().isInProject(file);
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

  @Override
  public final void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement element = useElementToTheLeft ? getElementToTheLeft(editor, file) : getElementToTheRight(editor,file);
    if (element == null){
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
   * @throws com.intellij.util.IncorrectOperationException
   *
   */
  public abstract void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException;

  @Nullable
  protected static PsiElement getElementToTheRight(Editor editor, @NotNull PsiFile file) {
    return file.findElementAt(editor.getCaretModel().getOffset());
  }

  @Nullable
  protected static PsiElement getElementToTheLeft(Editor editor, @NotNull PsiFile file) {
    return file.findElementAt(editor.getCaretModel().getOffset() - 1);
  }


}