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

package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.util.FileStructurePopup;
import com.intellij.ide.util.treeView.smartTree.TreeStructureUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.PlaceHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ViewStructureAction extends DumbAwareAction {

  public ViewStructureAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    final FileEditor fileEditor = e.getData(PlatformDataKeys.FILE_EDITOR);
    if (fileEditor == null) return;
    final VirtualFile virtualFile;

    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) {
      virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    }
    else {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile == null) return;

      virtualFile = psiFile.getVirtualFile();
    }
    String title = virtualFile == null? fileEditor.getName() : virtualFile.getName();

    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.file.structure");

    FileStructurePopup popup = createPopup(project, fileEditor);
    if (popup == null) return;

    popup.setTitle(title);
    popup.show();
  }

  @Nullable
  public static FileStructurePopup createPopup(@NotNull Project project, @NotNull FileEditor fileEditor) {
    StructureViewBuilder structureViewBuilder = fileEditor.getStructureViewBuilder();
    if (structureViewBuilder == null) return null;
    StructureView structureView = structureViewBuilder.createStructureView(fileEditor, project);
    StructureViewModel model = structureView.getTreeModel();
    if (model instanceof PlaceHolder) {
      //noinspection unchecked
      ((PlaceHolder)model).setPlace(TreeStructureUtil.PLACE);
    }
    return createStructureViewPopup(project, fileEditor, structureView);
  }

  private static FileStructurePopup createStructureViewPopup(Project project,
                                                             FileEditor fileEditor,
                                                             StructureView structureView) {
    return new FileStructurePopup(project, fileEditor, structureView, true);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    FileEditor fileEditor = e.getData(PlatformDataKeys.FILE_EDITOR);
    e.getPresentation().setEnabled(fileEditor != null && fileEditor.getStructureViewBuilder() != null);
  }
}
