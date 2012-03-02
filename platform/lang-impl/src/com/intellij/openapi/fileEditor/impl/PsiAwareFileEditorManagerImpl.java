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

package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.docking.DockManager;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

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

  public PsiAwareFileEditorManagerImpl(final Project project, final PsiManager psiManager, final WolfTheProblemSolver problemSolver, DockManager dockManager) {
    super(project, dockManager);
    myPsiManager = psiManager;
    myProblemSolver = problemSolver;
    myPsiTreeChangeListener = new MyPsiTreeChangeListener();
    myProblemListener = new MyProblemListener();
    registerExtraEditorDataProvider(new TextEditorPsiDataProvider(), null);
  }

  @Override
  public void projectOpened() {
    super.projectOpened();    //To change body of overridden methods use File | Settings | File Templates.
    myPsiManager.addPsiTreeChangeListener(myPsiTreeChangeListener);
    myProblemSolver.addProblemListener(myProblemListener);
  }

  @Override
  public Color getFileColor(@NotNull final VirtualFile file) {
    Color color = super.getFileColor(file);
    if (myProblemSolver.isProblemFile(file)) {
      return ColorUtil.toAlpha(color, WaverGraphicsDecorator.WAVE_ALPHA_KEY);
    }
    return color;
  }

  public boolean isProblem(@NotNull final VirtualFile file) {
    return myProblemSolver.isProblemFile(file);
  }

  public String getFileTooltipText(final VirtualFile file) {
    final StringBuilder tooltipText = new StringBuilder();
    final Module module = ModuleUtil.findModuleForFile(file, getProject());
    if (module != null) {
      tooltipText.append("[");
      tooltipText.append(module.getName());
      tooltipText.append("] ");
    }
    tooltipText.append(file.getPresentableUrl());
    return tooltipText.toString();
  }

  @Override
  protected Editor getOpenedEditor(final Editor editor, final boolean focusEditor) {
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
    public void propertyChanged(final PsiTreeChangeEvent e) {
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

    public void childAdded(PsiTreeChangeEvent event) {
      doChange(event);
    }

    public void childRemoved(PsiTreeChangeEvent event) {
      doChange(event);
    }

    public void childReplaced(PsiTreeChangeEvent event) {
      doChange(event);
    }

    public void childMoved(PsiTreeChangeEvent event) {
      doChange(event);
    }

    public void childrenChanged(PsiTreeChangeEvent event) {
      doChange(event);
    }

    private void doChange(final PsiTreeChangeEvent event) {
      final PsiFile psiFile = event.getFile();
      final VirtualFile currentFile = getCurrentFile();
      if (currentFile != null && psiFile != null && psiFile.getVirtualFile() == currentFile) {
        updateFileIcon(currentFile);
      }
    }
  }

  private class MyProblemListener extends WolfTheProblemSolver.ProblemListener {
    public void problemsAppeared(final VirtualFile file) {
      updateFile(file);
    }

    public void problemsDisappeared(VirtualFile file) {
      updateFile(file);
    }

    public void problemsChanged(VirtualFile file) {
      updateFile(file);
    }

    private void updateFile(final VirtualFile file) {
      queueUpdateFile(file);
    }
  }
}
