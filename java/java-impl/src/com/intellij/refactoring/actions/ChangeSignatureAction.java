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

import com.intellij.lang.Language;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangeSignatureAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && (elements[0] instanceof PsiMethod || elements[0] instanceof PsiClass);
  }

  protected boolean isAvailableOnElementInEditor(final PsiElement element, final Editor editor) {
    final Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(element.getProject()).getPsiFile(document);
    if (file == null) return false;
    PsiElement targetMember = findTargetMember(file, editor);
    if (targetMember == null) return false;
    final ChangeSignatureHandler targetHandler = getChangeSignatureHandler(targetMember.getLanguage());
    if (targetHandler == null) return false;
    return true;
  }

  @Nullable
  private static PsiElement findTargetMember(PsiFile file, Editor editor) {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiElement targetMember = findTargetMember(element);
    if (targetMember != null) return targetMember;

    final PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
    if (reference == null) return null;
    return reference.resolve();
  }

  @Nullable
  private static PsiElement findTargetMember(@Nullable PsiElement element) {
    if (element == null) return null;
    final ChangeSignatureHandler fileHandler = getChangeSignatureHandler(element.getLanguage());
    if (fileHandler != null) {
      final PsiElement targetMember = fileHandler.findTargetMember(element);
      if (targetMember != null) return targetMember;
    }
    final PsiReference reference = element.getReference();
    if (reference == null) return null;
    return reference.resolve();
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    final Language language = LangDataKeys.LANGUAGE.getData(dataContext);
    if (language != null) {
      return new RefactoringActionHandler() {
        public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
          editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
          final PsiElement targetMember = findTargetMember(file, editor);
          if (targetMember == null) return;
          final ChangeSignatureHandler handler = getChangeSignatureHandler(targetMember.getLanguage());
          if (handler == null) return;
          handler.invoke(project, new PsiElement[]{targetMember}, dataContext);
        }

        public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
          if (elements.length != 1) return;
          final PsiElement targetMember = findTargetMember(elements[0]);
          if (targetMember == null) return;
          final ChangeSignatureHandler handler = getChangeSignatureHandler(targetMember.getLanguage());
          if (handler == null) return;
          handler.invoke(project, new PsiElement[]{targetMember}, dataContext);
        }
      };
    }
    return null;
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return getChangeSignatureHandler(language) != null;
  }

  @Nullable
  private static ChangeSignatureHandler getChangeSignatureHandler(Language language) {
    return LanguageRefactoringSupport.INSTANCE.forLanguage(language).getChangeSignatureHandler();
  }
}
