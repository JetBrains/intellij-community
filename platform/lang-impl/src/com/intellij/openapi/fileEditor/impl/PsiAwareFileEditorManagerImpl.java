// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.ProblemListener;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PsiAwareFileEditorManagerImpl extends FileEditorManagerImpl {
  private final WolfTheProblemSolver myProblemSolver;

  /**
   * Updates icons for open files when project roots change
   */

  public PsiAwareFileEditorManagerImpl(@NotNull Project project) {
    super(project);

    myProblemSolver = WolfTheProblemSolver.getInstance(project);
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
  public boolean isProblem(@NotNull final VirtualFile file) {
    return myProblemSolver.isProblemFile(file);
  }

  @NotNull
  @Override
  public String getFileTooltipText(@NotNull final VirtualFile file) {
    final StringBuilder tooltipText = new StringBuilder();
    if (Registry.is("ide.tab.tooltip.module")) {
      final Module module = ModuleUtilCore.findModuleForFile(file, getProject());
      if (module != null && ModuleManager.getInstance(getProject()).getModules().length > 1) {
        tooltipText.append("[");
        tooltipText.append(module.getName());
        tooltipText.append("] ");
      }
    }
    tooltipText.append(super.getFileTooltipText(file));
    return tooltipText.toString();
  }

  private final class MyProblemListener implements ProblemListener {
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
