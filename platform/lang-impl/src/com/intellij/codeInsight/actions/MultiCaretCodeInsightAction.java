/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.project.Project;
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
public abstract class MultiCaretCodeInsightAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    final Editor hostEditor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (hostEditor == null) {
      return;
    }

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

    Editor hostEditor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (hostEditor == null) {
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

  private static void iterateOverCarets(@NotNull final Project project,
                                 @NotNull final Editor hostEditor,
                                 @NotNull final MultiCaretCodeInsightActionHandler handler) {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final PsiFile psiFile = documentManager.getCachedPsiFile(hostEditor.getDocument());
    documentManager.commitAllDocuments();

    hostEditor.getCaretModel().runForEachCaret(new CaretAction() {
      @Override
      public void perform(Caret caret) {
        Editor editor = hostEditor;
        if (psiFile != null) {
          Caret injectedCaret = InjectedLanguageUtil.getCaretForInjectedLanguageNoCommit(caret, psiFile);
          if (injectedCaret != null) {
            caret = injectedCaret;
            editor = caret.getEditor();
          }
        }
        final PsiFile file = PsiUtilBase.getPsiFileInEditor(caret, project);
        if (file != null) {
          handler.invoke(project, editor, caret, file);
        }
      }
    });
  }

  /**
   * During action status update this method is invoked for each caret in editor. If at least for a single caret it returns
   * <code>true</code>, action is considered enabled.
   */
  protected boolean isValidFor(@NotNull Project project, @NotNull Editor editor, @NotNull Caret caret, @NotNull PsiFile file) {
    return true;
  }

  @NotNull
  protected abstract MultiCaretCodeInsightActionHandler getHandler();

  protected String getCommandName() {
    String text = getTemplatePresentation().getText();
    return text == null ? "" : text;
  }
}
