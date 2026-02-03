// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for PSI-aware editor actions that need to support multiple carets.
 * Recognizes multi-root PSI and injected fragments, so different carets might be processed in context of different
 * {@link Editor} and {@link PsiFile} instances.
 * <p>
 * Implementations should implement {@link #getHandler()} method, and might override {@link
 * #isValidFor(Project, Editor, Caret, PsiFile)} method.
 *
 * @see MultiCaretCodeInsightActionHandler
 */
public abstract class MultiCaretCodeInsightAction extends AnAction implements PerformWithDocumentsCommitted {
  private static final Logger LOG = Logger.getInstance(MultiCaretCodeInsightAction.class);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    final Editor hostEditor = e.getData(CommonDataKeys.EDITOR);
    if (hostEditor == null) {
      return;
    }
    if (hostEditor.isDisposed()) {
      LOG.error("Action " + this + " invoked on a disposed editor in " + e.getDataContext());
      return;
    }
    if (!EditorModificationUtil.checkModificationAllowed(hostEditor)) return;
    PsiFile hostFile = PsiDocumentManager.getInstance(project).getPsiFile(hostEditor.getDocument());
    if (hostFile != null && !FileModificationService.getInstance().prepareFileForWrite(hostFile)) return;

    actionPerformedImpl(project, hostEditor);
  }

  public void actionPerformedImpl(final Project project, final Editor hostEditor) {
    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      MultiCaretCodeInsightActionHandler handler = getHandler();
      try {
        iterateOverCarets(project, hostEditor, handler);
      }
      finally {
        handler.postInvoke();
      }
    }), getCommandName(), DocCommandGroupId.noneGroupId(hostEditor.getDocument()));

    hostEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();

    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Editor hostEditor = e.getData(CommonDataKeys.EDITOR);
    if (hostEditor == null) {
      presentation.setEnabled(false);
      return;
    }
    if (hostEditor.isDisposed()) {
      LOG.error("Disposed editor in " + e.getDataContext() + " for " + this);
      presentation.setEnabled(false);
      return;
    }

    final Ref<Boolean> enabled  = new Ref<>(Boolean.FALSE);
    iterateOverCarets(project, hostEditor, new MultiCaretCodeInsightActionHandler() {
      @Override
      public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull Caret caret, @NotNull PsiFile file) {
        if (isValidFor(project, editor, caret, file)) {
          enabled.set(Boolean.TRUE);
        }
      }
    });
    presentation.setEnabled(enabled.get());
  }

  protected abstract @NotNull MultiCaretCodeInsightActionHandler getHandler();

  /**
   * During action status update this method is invoked for each caret in editor. If at least for a single caret it returns
   * {@code true}, action is considered enabled.
   */
  protected boolean isValidFor(@NotNull Project project, @NotNull Editor editor, @NotNull Caret caret, @NotNull PsiFile file) {
    return true;
  }

  private static void iterateOverCarets(final @NotNull Project project,
                                        final @NotNull Editor hostEditor,
                                        final @NotNull MultiCaretCodeInsightActionHandler handler) {
    PsiFile hostFile = PsiDocumentManager.getInstance(project).getPsiFile(hostEditor.getDocument());

    hostEditor.getCaretModel().runForEachCaret(caret -> {
      Editor editor = hostEditor;
      if (hostFile != null) {
        Caret injectedCaret = InjectedLanguageUtil.getCaretForInjectedLanguageNoCommit(caret, hostFile);
        if (injectedCaret != null) {
          caret = injectedCaret;
          editor = caret.getEditor();
        }
      }
      final PsiFile file = PsiUtilBase.getPsiFileInEditor(caret, project);
      if (file != null) {
        handler.invoke(project, editor, caret, file);
      }
    });
  }

  protected @NlsContexts.Command String getCommandName() {
    String text = getTemplatePresentation().getText();
    return text == null ? "" : text;
  }
}
