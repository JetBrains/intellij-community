// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters.impl;

import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.filters.FileHyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfoBase;
import com.intellij.execution.filters.HyperlinkInfoFactory;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

@ApiStatus.Internal
public abstract class MultipleFilesHyperlinkInfoBase extends HyperlinkInfoBase implements FileHyperlinkInfo {
  protected final int myLineNumber;
  protected final Project myProject;
  private final HyperlinkInfoFactory.@Nullable HyperlinkHandler myAction;

  public MultipleFilesHyperlinkInfoBase(int lineNumber,
                                        @NotNull Project project,
                                        @Nullable HyperlinkInfoFactory.HyperlinkHandler action) {
    myLineNumber = lineNumber;
    myProject = project;
    myAction = action;
  }

  @Override
  public void navigate(final @NotNull Project project, @Nullable RelativePoint hyperlinkLocationPoint) {
    Editor originalEditor;
    if (hyperlinkLocationPoint != null) {
      DataManager dataManager = DataManager.getInstance();
      DataContext dataContext = dataManager.getDataContext(hyperlinkLocationPoint.getOriginalComponent());
      originalEditor = CommonDataKeys.EDITOR.getData(dataContext);
    }
    else {
      originalEditor = null;
    }

    JFrame frame = WindowManager.getInstance().getFrame(project);
    int width = frame != null ? frame.getSize().width : 200;
    GotoFileCellRenderer renderer = new GotoFileCellRenderer(width);

    boolean navigated = new PsiTargetNavigator<>(() -> getFiles(project))
      .title(ExecutionBundle.message("popup.title.choose.target.file"))
      .presentationProvider(element -> renderer.computePresentation(element))
      .navigate(hyperlinkLocationPoint, ExecutionBundle.message("popup.title.choose.target.file"), project, file -> {
        open(file.getVirtualFile(), originalEditor);
        return true;
      });
    if (!navigated) {
      showNotFound(project, hyperlinkLocationPoint);
    }
  }

  protected void showNotFound(final @NotNull Project project, @Nullable RelativePoint hyperlinkLocationPoint) {
  }

  public abstract @NotNull List<PsiFile> getFiles(@NotNull Project project);

  private void open(@NotNull VirtualFile file, Editor originalEditor) {
    Document document = FileDocumentManager.getInstance().getDocument(file, myProject);
    int offset = 0;
    if (document != null && myLineNumber >= 0 && myLineNumber < document.getLineCount()) {
      offset = document.getLineStartOffset(myLineNumber);
    }
    OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, file, offset);
    Editor editor = FileEditorManager.getInstance(myProject).openTextEditor(descriptor, true);
    if (myAction != null && editor != null) {
      if (editor instanceof EditorEx) {
        ((EditorEx)editor).setCaretEnabled(false);
        try {
          myAction.onLinkFollowed(myProject, file, editor, originalEditor);
        }
        finally {
          ((EditorEx)editor).setCaretEnabled(true);
        }
      }
      else {
        myAction.onLinkFollowed(myProject, file, editor, originalEditor);
      }
    }
  }
}
