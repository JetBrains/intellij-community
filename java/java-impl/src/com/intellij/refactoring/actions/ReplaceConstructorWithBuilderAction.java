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
 * User: anna
 * Date: 07-May-2008
 */
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.replaceConstructorWithBuilder.ReplaceConstructorWithBuilderHandler;
import org.jetbrains.annotations.NotNull;

public class ReplaceConstructorWithBuilderAction extends BaseRefactoringAction{
  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement elementAt = file.findElementAt(offset);
    final PsiClass psiClass = ReplaceConstructorWithBuilderHandler.getParentNamedClass(elementAt);
    return psiClass != null && psiClass.getConstructors().length > 0 && !psiClass.isEnum();
  }

  protected boolean isEnabledOnElements(@NotNull final PsiElement[] elements) {
    return false;
  }

  protected RefactoringActionHandler getHandler(@NotNull final DataContext dataContext) {
    return new ReplaceConstructorWithBuilderHandler();
  }
}
