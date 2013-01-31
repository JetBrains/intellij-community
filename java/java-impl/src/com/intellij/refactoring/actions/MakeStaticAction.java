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
 * User: dsl
 * Date: Apr 15, 2002
 * Time: 1:32:20 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.makeStatic.MakeStaticHandler;
import org.jetbrains.annotations.NotNull;

public class MakeStaticAction extends BaseRefactoringAction {
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isEnabledOnElements(@NotNull PsiElement[] elements) {
    return (elements.length == 1) && (elements[0] instanceof PsiMethod) && !((PsiMethod)elements[0]).isConstructor();
  }

  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, @NotNull final Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    if (element instanceof PsiIdentifier) {
      element = element.getParent();
    }
    return element instanceof PsiTypeParameterListOwner &&
           MakeStaticHandler.validateTarget((PsiTypeParameterListOwner) element) == null;
  }

  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new MakeStaticHandler();
  }
}
