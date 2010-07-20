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

/**
 * class InlineAction
 * created Aug 28, 2001
 * @author Jeka
 */
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.lang.refactoring.InlineHandlers;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.inline.InlineRefactoringActionHandler;
import org.jetbrains.annotations.Nullable;

public class InlineAction extends BasePlatformRefactoringAction {

  public InlineAction() {
    setInjectedContext(true);
  }

  public boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isAvailableOnElementInEditor(PsiElement element, Editor editor) {
    return hasInlineActionHandler(element, PsiUtilBase.getLanguageInEditor(editor, element.getProject()), editor);
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && hasInlineActionHandler(elements [0], null, null);
  }

  private static boolean hasInlineActionHandler(PsiElement element, @Nullable Language editorLanguage, Editor editor) {
    for(InlineActionHandler handler: Extensions.getExtensions(InlineActionHandler.EP_NAME)) {
      if (handler.isEnabledOnElement(element, editor)) {
        return true;
      }
    }
    return InlineHandlers.getInlineHandlers(
      editorLanguage != null ? editorLanguage :element.getLanguage()
    ).size() > 0;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new InlineRefactoringActionHandler();
  }

  protected boolean isAvailableForLanguage(Language language) {
    for(InlineActionHandler handler: Extensions.getExtensions(InlineActionHandler.EP_NAME)) {
      if (handler.isEnabledForLanguage(language)) {
        return true;
      }
    }
    return InlineHandlers.getInlineHandlers(language).size() > 0;
  }
}
