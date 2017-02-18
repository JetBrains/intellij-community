/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.docking.DockManager;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PsiAwareFileEditorManagerImpl extends FileEditorManagerImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.text.PsiAwareFileEditorManagerImpl");

  private final PsiManager myPsiManager;
  private final WolfTheProblemSolver myProblemSolver;

  /**
   * Updates icons for open files when project roots change
   */
  private final MyPsiTreeChangeListener myPsiTreeChangeListener;
  private final WolfTheProblemSolver.ProblemListener myProblemListener;

  public PsiAwareFileEditorManagerImpl(final Project project,
                                       final PsiManager psiManager,
                                       final WolfTheProblemSolver problemSolver,
                                       DockManager dockManager) {
    super(project, dockManager);

    myPsiManager = psiManager;
    myProblemSolver = problemSolver;
    myPsiTreeChangeListener = new MyPsiTreeChangeListener();
    myProblemListener = new MyProblemListener();
    registerExtraEditorDataProvider(new TextEditorPsiDataProvider(), null);

    // reinit syntax highlighter for Groovy. In power save mode keywords are highlighted by GroovySyntaxHighlighter insteadof
    // GrKeywordAndDeclarationHighlighter. So we need to drop caches for token types attributes in LayeredLexerEditorHighlighter
    project.getMessageBus().connect().subscribe(PowerSaveMode.TOPIC, new PowerSaveMode.Listener() {
      @Override
      public void powerSaveStateChanged() {
        UIUtil.invokeLaterIfNeeded(() -> {
          for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            ((EditorEx)editor).reinitSettings();
          }
        });
      }
    });
  }

  @Override
  protected void projectOpened(@NotNull MessageBusConnection connection) {
    super.projectOpened(connection);

    myPsiManager.addPsiTreeChangeListener(myPsiTreeChangeListener);
    myProblemSolver.addProblemListener(myProblemListener);
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
    tooltipText.append(FileUtil.getLocationRelativeToUserHome(file.getPresentableUrl()));
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

  /**
   * Updates attribute of open files when roots change
   */
  private final class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {
    @Override
    public void propertyChanged(@NotNull final PsiTreeChangeEvent e) {
      if (PsiTreeChangeEvent.PROP_ROOTS.equals(e.getPropertyName())) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        final VirtualFile[] openFiles = getOpenFiles();
        for (int i = openFiles.length - 1; i >= 0; i--) {
          final VirtualFile file = openFiles[i];
          LOG.assertTrue(file != null);
          updateFileIcon(file);
        }
      }
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      doChange(event);
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      doChange(event);
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      doChange(event);
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
      doChange(event);
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      doChange(event);
    }

    private void doChange(final PsiTreeChangeEvent event) {
      final PsiFile psiFile = event.getFile();
      if (psiFile == null) return;
      VirtualFile file = psiFile.getVirtualFile();
      if (file == null) return;
      FileEditor[] editors = getAllEditors(file);
      if (editors.length == 0) return;

      final VirtualFile currentFile = getCurrentFile();
      if (currentFile != null && Comparing.equal(psiFile.getVirtualFile(), currentFile)) {
        updateFileIcon(currentFile);
      }
    }
  }

  private class MyProblemListener extends WolfTheProblemSolver.ProblemListener {
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
