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

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 05-Nov-2006
 * Time: 18:04:58
 */
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.intention.impl.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;

public abstract class PsiElementBaseIntentionAction extends BaseIntentionAction {

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

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!file.getManager().isInProject(file)) return false;
    final CaretModel caretModel = editor.getCaretModel();
    final int position = caretModel.getOffset();
    final PsiElement element = file.findElementAt(position);
    if (element == null) return false;
    return isAvailable(project, editor, element);
  }
}