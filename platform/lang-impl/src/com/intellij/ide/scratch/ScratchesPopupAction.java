// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.ide.actions.RecentLocationsAction;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorState;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class ScratchesPopupAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    RecentLocationsAction.showPopup(
      project, false, LangBundle.message("scratch.file.popup.title"),
      LangBundle.message("scratch.file.popup.changed.title"),
      LangBundle.message("scratch.file.popup.title.empty.text"),
      changed -> {
        String path = ScratchFileService.getInstance().getRootPath(ScratchRootType.getInstance());
        VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath(path);
        if (rootDir == null || !rootDir.exists() || !rootDir.isDirectory()) return Collections.emptyList();
        Condition<? super VirtualFile> condition;
        if (!changed) {
          condition = Conditions.alwaysTrue();
        }
        else {
          Set<VirtualFile> files = JBIterable.from(IdeDocumentHistory.getInstance(project).getChangePlaces()).map(o -> o.getFile()).toSet();
          condition = files::contains;
        }
        List<IdeDocumentHistoryImpl.PlaceInfo> result = new ArrayList<>();
        VfsUtilCore.visitChildrenRecursively(rootDir, new VirtualFileVisitor<>(VirtualFileVisitor.SKIP_ROOT) {
          @Override
          public boolean visitFile(@NotNull VirtualFile file) {
            if (file.isDirectory() || !file.isValid() || !condition.value(file)) return true;
            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document == null) return true;
            RangeMarker caret = document.createRangeMarker(0, 0);
            result.add(new IdeDocumentHistoryImpl.PlaceInfo(file, new TextEditorState(), "text-editor", null, caret));
            return result.size() < 1000;
          }
        });
        return result;
      },
      toRemove -> {
        PsiManager psiManager = PsiManager.getInstance(project);
        List<@NotNull PsiFile> files = ContainerUtil.mapNotNull(toRemove, o -> psiManager.findFile(o.getFile()));
        DeleteHandler.deletePsiElement(files.toArray(PsiElement.EMPTY_ARRAY), project, false);
      });
  }
}
