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
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.ide.IdeEventQueue;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BaseRefactoringAction extends AnAction {
  private final Condition<Language> myLanguageCondition = language -> isAvailableForLanguage(language);

  protected abstract boolean isAvailableInEditorOnly();

  protected abstract boolean isEnabledOnElements(@NotNull PsiElement[] elements);

  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    return true;
  }

  protected boolean hasAvailableHandler(@NotNull DataContext dataContext) {
    final RefactoringActionHandler handler = getHandler(dataContext);
    if (handler != null) {
      if (handler instanceof ContextAwareActionHandler) {
        final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
        if (editor != null && file != null && !((ContextAwareActionHandler)handler).isAvailableForQuickList(editor, file, dataContext)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  @Nullable
  protected abstract RefactoringActionHandler getHandler(@NotNull DataContext dataContext);

  @Override
  public final void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    final PsiElement[] elements = getPsiElementArray(dataContext);
    int eventCount = IdeEventQueue.getInstance().getEventCount();
    RefactoringActionHandler handler;
    try {
      handler = getHandler(dataContext);
    }
    catch (ProcessCanceledException ignored) {
      return;
    }
    if (handler == null) {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message(
        "error.wrong.caret.position.symbol.to.refactor")), RefactoringBundle.getCannotRefactorMessage(null), null);
      return;
    }

    if (!InplaceRefactoring.canStartAnotherRefactoring(editor, project, handler, elements)) {
      InplaceRefactoring.unableToStartWarning(project, editor);
      return;
    }

    if (InplaceRefactoring.getActiveInplaceRenamer(editor) == null) {
      final LookupEx lookup = LookupManager.getActiveLookup(editor);
      if (lookup instanceof LookupImpl) {
        Runnable command = () -> ((LookupImpl)lookup).finishLookup(Lookup.NORMAL_SELECT_CHAR);
        Document doc = editor.getDocument();
        DocCommandGroupId group = DocCommandGroupId.noneGroupId(doc);
        CommandProcessor.getInstance().executeCommand(editor.getProject(), command, "Completion", group, UndoConfirmationPolicy.DEFAULT, doc);
      }
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

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setVisible(true);
    presentation.setEnabled(true);
    DataContext dataContext = e.getDataContext();
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || isHidden()) {
      hideAction(e);
      return;
    }

    Editor editor = e.getData(CommonDataKeys.EDITOR);
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    if (file != null) {
      if (file instanceof PsiCompiledElement || !isAvailableForFile(file)) {
        hideAction(e);
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
      PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
      Language[] languages = e.getData(LangDataKeys.CONTEXT_LANGUAGES);
      if (element == null || !isAvailableForLanguage(element.getLanguage())) {
        if (file == null) {
          hideAction(e);
          return;
        }
        element = getElementAtCaret(editor, file);
      }

      if (element == null || element instanceof SyntheticElement || languages == null) {
        hideAction(e);
        return;
      }

      boolean isVisible = ContainerUtil.find(languages, myLanguageCondition) != null;
      if (isVisible) {
        boolean isEnabled = isAvailableOnElementInEditorAndFile(element, editor, file, dataContext);
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
    disableAction(e);
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
    if (editor.getSelectionModel().hasSelection()) {
      if (caret == editor.getSelectionModel().getSelectionEnd()) {
        return Math.max(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd() - 1);
      }
    }

    return caret;
  }

  private static void disableAction(final AnActionEvent e) {
    e.getPresentation().setEnabled(false);
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
      PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
      if (element != null) {
        psiElements = new PsiElement[]{element};
      }
    }

    if (psiElements == null) return PsiElement.EMPTY_ARRAY;

    List<PsiElement> filtered = null;
    for (PsiElement element : psiElements) {
      if (element instanceof SyntheticElement) {
        if (filtered == null) filtered = new ArrayList<>(Collections.singletonList(element));
        filtered.remove(element);
      }
    }
    return filtered == null ? psiElements : PsiUtilCore.toPsiElementArray(filtered);
  }

}
