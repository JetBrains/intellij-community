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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.IdeEventQueue;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class BaseRefactoringAction extends AnAction {
  protected abstract boolean isAvailableInEditorOnly();

  protected abstract boolean isEnabledOnElements(PsiElement[] elements);

  protected boolean isAvailableOnElementInEditor(final PsiElement element, final Editor editor) {
    return true;
  }

  @Nullable
  protected abstract RefactoringActionHandler getHandler(DataContext dataContext);

  public final void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    final PsiElement[] elements = getPsiElementArray(dataContext);
    int eventCount = IdeEventQueue.getInstance().getEventCount();
    RefactoringActionHandler handler = getHandler(dataContext);
    if (handler == null) {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message(
        "error.wrong.caret.position.symbol.to.refactor")), RefactoringBundle.getCannotRefactorMessage(null), null);
      return;
    }
    IdeEventQueue.getInstance().setEventCount(eventCount);
    if (editor != null) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) return;
      DaemonCodeAnalyzer.getInstance(project).autoImportReferenceAtCursor(editor, file);
      handler.invoke(project, editor, file, dataContext);
    }
    else {
      handler.invoke(project, elements, dataContext);
    }
  }

  protected boolean isEnabledOnDataContext(DataContext dataContext) {
    return false;
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setVisible(true);
    presentation.setEnabled(true);
    DataContext dataContext = e.getDataContext();
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null || isHidden()) {
      hideAction(e);
      return;
    }

    Editor editor = e.getData(PlatformDataKeys.EDITOR);
    PsiFile file = e.getData(LangDataKeys.PSI_FILE);
    if (file != null) {
      if (file instanceof PsiCompiledElement || !isAvailableForFile(file)) {
        disableAction(e);
        return;
      }
    }

    if (editor == null) {
      if (isAvailableInEditorOnly()) {
        hideAction(e);
        return;
      }
      final PsiElement[] elements = getPsiElementArray(dataContext);
      final boolean isEnabled = isEnabledOnDataContext(dataContext) || elements.length != 0 && isEnabledOnElements(elements);
      if (!isEnabled) {
        disableAction(e);
      }
    }
    else {
      PsiElement element = e.getData(LangDataKeys.PSI_ELEMENT);
      if (element == null || !isAvailableForLanguage(element.getLanguage())) {
        if (file == null) {
          hideAction(e);
          return;
        }
        element = getElementAtCaret(editor, file);
      }
      boolean isVisible = element != null &&
                          !(element instanceof SyntheticElement) &&
                          isAvailableForLanguage(PsiUtilBase.getLanguageInEditor(editor, project));
      if (isVisible) {
        boolean isEnabled = isAvailableOnElementInEditor(element, editor);
        if (!isEnabled) {
          disableAction(e);
        }
      }
      else {
        hideAction(e);
      }
    }
  }

  private static void hideAction(AnActionEvent e) {
    e.getPresentation().setVisible(false);
  }

  protected boolean isHidden() {
    return false;
  }

  public static PsiElement getElementAtCaret(final Editor editor, final PsiFile file) {
    final int offset = fixCaretOffset(editor);
    PsiElement element = file.findElementAt(offset);
    if (element == null && offset == file.getTextLength()) {
      element = file.findElementAt(offset - 1);
    }

    if (element instanceof PsiWhiteSpace) {
      element = file.findElementAt(element.getTextRange().getStartOffset() - 1);
    }
    return element;
  }

  private static int fixCaretOffset(final Editor editor) {
    final int caret = editor.getCaretModel().getOffset();
    if (editor.getSelectionModel().hasSelection() && !editor.getSelectionModel().hasBlockSelection()) {
      if (caret == editor.getSelectionModel().getSelectionEnd()) {
        return Math.max(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd() - 1);
      }
    }

    return caret;
  }

  private static void disableAction(final AnActionEvent e) {
    e.getPresentation().setEnabled(false);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      hideAction(e);
    }
  }

  protected boolean isAvailableForLanguage(Language language) {
    return language.isKindOf(StdFileTypes.JAVA.getLanguage());
  }

  protected boolean isAvailableForFile(PsiFile file) {
    return true;
  }

  @NotNull
  public static PsiElement[] getPsiElementArray(DataContext dataContext) {
    PsiElement[] psiElements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    if (psiElements == null || psiElements.length == 0) {
      PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
      if (element != null) {
        psiElements = new PsiElement[]{element};
      }
    }

    if (psiElements == null) return PsiElement.EMPTY_ARRAY;

    List<PsiElement> filtered = null;
    for (PsiElement element : psiElements) {
      if (element instanceof SyntheticElement) {
        if (filtered == null) filtered = new ArrayList<PsiElement>(Arrays.asList(element));
        filtered.remove(element);
      }
    }
    return filtered == null ? psiElements : filtered.toArray(new PsiElement[filtered.size()]);
  }

}