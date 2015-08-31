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
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.invertBoolean.InvertBooleanDelegate;
import com.intellij.refactoring.invertBoolean.InvertBooleanHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class InvertBooleanAction extends BaseRefactoringAction {
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isEnabledOnElements(@NotNull PsiElement[] elements) {
    if (elements.length == 1 && elements[0] != null) {
      for (InvertBooleanDelegate delegate : Extensions.getExtensions(InvertBooleanDelegate.EP_NAME)) {
        if (delegate.isVisibleOnElement(elements[0])) {
          return true;
        }
      }
    }
    return false;
  }

  protected boolean isAvailableOnElementInEditorAndFile(@NotNull final PsiElement element, 
                                                        @NotNull final Editor editor, 
                                                        @NotNull PsiFile file,
                                                        @NotNull DataContext context) {
    for (InvertBooleanDelegate delegate : Extensions.getExtensions(InvertBooleanDelegate.EP_NAME)) {
      if (delegate.isAvailableOnElement(element)) {
        return true;
      }
    }
    return false;
  }

  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new InvertBooleanHandler();
  }
}
