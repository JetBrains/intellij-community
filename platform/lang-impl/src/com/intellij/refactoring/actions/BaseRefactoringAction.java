// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.ide.IdeEventQueue;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringUsageCollector;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.Predicate;

public abstract class BaseRefactoringAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected abstract boolean isAvailableInEditorOnly();

  protected abstract boolean isEnabledOnElements(PsiElement @NotNull [] elements);

  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element,
                                                        @NotNull Editor editor,
                                                        @NotNull PsiFile file,
                                                        @NotNull DataContext context,
                                                        @NotNull String place) {
    if (ActionPlaces.isPopupPlace(place) || place.contains(ActionPlaces.EDITOR_FLOATING_TOOLBAR)) {
      final RefactoringActionHandler handler = getHandler(context);
      if (handler == null) return false;
      if (handler instanceof ContextAwareActionHandler contextAwareActionHandler) {
        if (!contextAwareActionHandler.isAvailableForQuickList(editor, file, context)) {
          return false;
        }
      }
    }

    return isAvailableOnElementInEditorAndFile(element, editor, file, context);
  }

  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element,
                                                        @NotNull Editor editor,
                                                        @NotNull PsiFile file,
                                                        @NotNull DataContext context) {
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

  protected abstract @Nullable RefactoringActionHandler getHandler(@NotNull DataContext dataContext);

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = e.getProject();
    if (project == null) return;
    int eventCount = IdeEventQueue.getInstance().getEventCount();
    if (!PsiDocumentManager.getInstance(project).commitAllDocumentsUnderProgress()) {
      return;
    }

    RefactoringActionHandler handler;
    try {
      handler = getHandler(dataContext);
    }
    catch (ProcessCanceledException ignored) {
      return;
    }
    IdeEventQueue.getInstance().setEventCount(eventCount);
    performRefactoringAction(project, dataContext, handler);
  }

  @ApiStatus.Internal
  public static void performRefactoringAction(@NotNull Project project,
                                              @NotNull DataContext dataContext,
                                              @Nullable RefactoringActionHandler handler) {
    final Editor editor = dataContext.getData(CommonDataKeys.EDITOR);

    if (handler == null) {
      String message =
        RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.symbol.to.refactor"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, RefactoringBundle.getCannotRefactorMessage(null), null);
      return;
    }

    final PsiElement[] elements = getPsiElementArray(dataContext);
    InplaceRefactoring activeInplaceRenamer = InplaceRefactoring.getActiveInplaceRenamer(editor);
    if (activeInplaceRenamer != null && !InplaceRefactoring.canStartAnotherRefactoring(editor, handler, elements)) {
      InplaceRefactoring.unableToStartWarning(project, editor);
      return;
    }

    if (activeInplaceRenamer == null) {
      final LookupEx lookup = LookupManager.getActiveLookup(editor);
      if (lookup instanceof LookupImpl) {
        Runnable command = () -> ((LookupImpl)lookup).finishLookup(Lookup.NORMAL_SELECT_CHAR);
        Document doc = editor.getDocument();
        DocCommandGroupId group = DocCommandGroupId.noneGroupId(doc);
        CommandProcessor.getInstance()
          .executeCommand(editor.getProject(), command, ApplicationBundle.message("title.code.completion"), group,
                          UndoConfirmationPolicy.DEFAULT, doc);
      }
    }

    final PsiFile file = editor != null ? PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) : null;
    final Language language = file != null
                              ? file.getLanguage()
                              : (elements.length > 0 ? elements[0].getLanguage() : null);
    ArrayList<EventPair<?>> data = new ArrayList<>();
    data.add(RefactoringUsageCollector.HANDLER.with(handler.getClass()));
    data.add(EventFields.Language.with(language));
    if (elements.length > 0) {
      data.add(RefactoringUsageCollector.ELEMENT.with(elements[0].getClass()));
    }

    RefactoringUsageCollector.HANDLER_INVOKED.log(project, data);

    if (editor != null) {
      if (file == null) return;
      DaemonCodeAnalyzer.getInstance(project).autoImportReferenceAtCursor(editor, file);
      handler.invoke(project, editor, file, dataContext);
    }
    else {
      handler.invoke(project, elements, dataContext);
    }
  }

  protected boolean isEnabledOnDataContext(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(true);
    DataContext dataContext = e.getDataContext();
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || isHidden()) {
      hideAction(e);
      return;
    }

    Editor editor = e.getData(CommonDataKeys.EDITOR);
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    if (file != null) {
      if (file instanceof PsiCompiledElement && disableOnCompiledElement() || !isAvailableForFile(file)) {
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
      boolean availableForLanguage = ContainerUtil.exists(elements, element -> isAvailableForLanguage(element.getLanguage()));
      if (!availableForLanguage) {
        hideAction(e);
        return;
      }
      final boolean isEnabled = isEnabledOnDataContext(dataContext) || isEnabledOnElements(elements);
      if (!isEnabled) {
        disableAction(e);
      }
      else {
        updateActionText(e);
      }
    }
    else {
      PsiElement element = findRefactoringTargetInEditor(dataContext, this::isAvailableForLanguage);
      if (element != null) {
        boolean isEnabled = file != null && isAvailableOnElementInEditorAndFile(element, editor, file, dataContext, e.getPlace());
        if (!isEnabled) {
          disableAction(e);
        }
        else {
          updateActionText(e);
        }
      }
      else {
        hideAction(e);
      }
    }
  }

  @ApiStatus.Internal
  public static PsiElement findRefactoringTargetInEditor(@NotNull DataContext dataContext,
                                                         @NotNull Predicate<? super Language> elementLanguagePredicate) {
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    PsiFile file = dataContext.getData(CommonDataKeys.PSI_FILE);
    PsiElement element = dataContext.getData(CommonDataKeys.PSI_ELEMENT);
    Language[] languages = dataContext.getData(LangDataKeys.CONTEXT_LANGUAGES);
    if (element == null || element instanceof SyntheticElement || !elementLanguagePredicate.test(element.getLanguage())) {
      if (file == null || editor == null) {
        return null;
      }
      element = getElementAtCaret(editor, file);
    }

    if (element == null || element instanceof SyntheticElement || languages == null) {
      return null;
    }

    if (ContainerUtil.find(languages, elementLanguagePredicate::test) == null) {
      return null;
    }
    return element;
  }

  private void updateActionText(AnActionEvent e) {
    String actionText = getActionName(e.getDataContext());
    if (actionText != null) {
      e.getPresentation().setText(actionText);
    }
  }

  protected @NlsActions.ActionText @Nullable String getActionName(@NotNull DataContext dataContext) {
    return null;
  }

  protected boolean disableOnCompiledElement() {
    return true;
  }

  private static void hideAction(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(false);
    disableAction(e);
  }

  protected boolean isHidden() {
    return false;
  }

  public static PsiElement getElementAtCaret(final @NotNull Editor editor, final PsiFile file) {
    return CommonRefactoringUtil.getElementAtCaret(editor, file);
  }

  private static void disableAction(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(false);
  }

  protected boolean isAvailableForLanguage(Language language) {
    return true;
  }

  protected boolean isAvailableForFile(PsiFile file) {
    return true;
  }

  public static PsiElement @NotNull [] getPsiElementArray(@NotNull DataContext dataContext) {
    return CommonRefactoringUtil.getPsiElementArray(dataContext);
  }
}
