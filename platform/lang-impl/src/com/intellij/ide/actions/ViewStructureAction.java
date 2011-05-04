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

package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.util.FileStructureDialog;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class ViewStructureAction extends AnAction {
  public ViewStructureAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    final FileEditor fileEditor = e.getData(PlatformDataKeys.FILE_EDITOR);
    if (editor == null) return;
    if (fileEditor == null) return;

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) return;

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.file.structure");

    Navigatable navigatable = e.getData(PlatformDataKeys.NAVIGATABLE);
    DialogWrapper dialog = createDialog(editor, project, navigatable, fileEditor);
    if (dialog != null) {
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        dialog.setTitle(virtualFile.getName());
      }
      dialog.show();
    }
  }

  @Nullable
  private static DialogWrapper createDialog(final Editor editor, Project project, Navigatable navigatable, final FileEditor fileEditor) {
    final StructureViewBuilder structureViewBuilder = fileEditor.getStructureViewBuilder();
    if (structureViewBuilder == null) return null;
    StructureView structureView = structureViewBuilder.createStructureView(fileEditor, project);
    return createStructureViewBasedDialog(structureView.getTreeModel(), editor, project, navigatable, structureView);
  }

  public static FileStructureDialog createStructureViewBasedDialog(final StructureViewModel structureViewModel,
                                                                   final Editor editor,
                                                                   final Project project,
                                                                   final Navigatable navigatable,
                                                                   final @NotNull Disposable alternativeDisposable) {
    return new FileStructureDialog(structureViewModel, editor, project, navigatable, alternativeDisposable, true);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      presentation.setEnabled(false);
      return;
    }

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) {
      presentation.setEnabled(false);
      return;
    }
    final VirtualFile virtualFile = psiFile.getVirtualFile();

    if (virtualFile == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(
      StructureViewBuilder.PROVIDER.getStructureViewBuilder(virtualFile.getFileType(), virtualFile, project) != null );
  }
}
