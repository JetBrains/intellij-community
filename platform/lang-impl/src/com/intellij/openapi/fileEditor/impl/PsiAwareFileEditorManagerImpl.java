// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.ProblemListener;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.docking.DockManager;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PsiAwareFileEditorManagerImpl extends FileEditorManagerImpl {
  private final PsiManager myPsiManager;
  private final WolfTheProblemSolver myProblemSolver;

  /**
   * Updates icons for open files when project roots change
   */
  private final FileEditorPsiTreeChangeListener myPsiTreeChangeListener;

  public PsiAwareFileEditorManagerImpl(final Project project,
                                       final PsiManager psiManager,
                                       final WolfTheProblemSolver problemSolver,
                                       DockManager dockManager) {
    super(project, dockManager);

    myPsiManager = psiManager;
    myProblemSolver = problemSolver;
    myPsiTreeChangeListener = new FileEditorPsiTreeChangeListener(this);
    registerExtraEditorDataProvider(new TextEditorPsiDataProvider(), null);

    // reinit syntax highlighter for Groovy. In power save mode keywords are highlighted by GroovySyntaxHighlighter insteadof
    // GrKeywordAndDeclarationHighlighter. So we need to drop caches for token types attributes in LayeredLexerEditorHighlighter
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(PowerSaveMode.TOPIC, new PowerSaveMode.Listener() {
      @Override
      public void powerSaveStateChanged() {
        UIUtil.invokeLaterIfNeeded(() -> {
          for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            ((EditorEx)editor).reinitSettings();
          }
        });
      }
    });

    connection.subscribe(ProblemListener.TOPIC, new MyProblemListener());
  }

  @Override
  protected void projectOpened(@NotNull MessageBusConnection connection) {
    super.projectOpened(connection);

    myPsiManager.addPsiTreeChangeListener(myPsiTreeChangeListener);
  }

  @Override
  public boolean isProblem(@NotNull final VirtualFile file) {
    return myProblemSolver.isProblemFile(file);
  }

  @NotNull
  @Override
  public String getFileTooltipText(@NotNull final VirtualFile file) {
    final StringBuilder tooltipText = new StringBuilder();
    final Module module = ModuleUtilCore.findModuleForFile(file, getProject());
    if (module != null) {
      tooltipText.append("[");
      tooltipText.append(module.getName());
      tooltipText.append("] ");
    }
    tooltipText.append(super.getFileTooltipText(file));
    return tooltipText.toString();
  }

  @Override
  protected Editor getOpenedEditor(@NotNull final Editor editor, final boolean focusEditor) {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = editor.getDocument();
    PsiFile psiFile = documentManager.getPsiFile(document);
    if (!focusEditor || documentManager.isUncommited(document)) {
      return editor;
    }

    return InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, psiFile);
  }

  private class MyProblemListener implements ProblemListener {
    @Override
    public void problemsAppeared(@NotNull final VirtualFile file) {
      updateFile(file);
    }

    @Override
    public void problemsDisappeared(@NotNull VirtualFile file) {
      updateFile(file);
    }

    @Override
    public void problemsChanged(@NotNull VirtualFile file) {
      updateFile(file);
    }

    private void updateFile(@NotNull VirtualFile file) {
      queueUpdateFile(file);
    }
  }
}
