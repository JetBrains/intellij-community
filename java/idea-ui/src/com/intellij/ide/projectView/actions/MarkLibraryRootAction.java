// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.libraries.ui.impl.RootDetectionUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.DefaultLibraryRootsComponentDescriptor;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MarkLibraryRootAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) return;

    final List<VirtualFile> jars = getRoots(e);
    if (jars.isEmpty()) return;

    final List<OrderRoot> roots = RootDetectionUtil.detectRoots(jars, null, project, new DefaultLibraryRootsComponentDescriptor());
    new CreateLibraryFromFilesDialog(project, roots).show();
  }

  private static @NotNull List<VirtualFile> getRoots(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (project == null || files == null || files.length == 0) return Collections.emptyList();

    List<VirtualFile> roots = new ArrayList<>();
    for (VirtualFile file : files) {
      if (file.isDirectory()) {
        roots.add(file);
      }
      else {
        final VirtualFile root = JarFileSystem.getInstance().getJarRootForLocalFile(file);
        if (root != null) {
          roots.add(root);
        }
      }
    }
    return roots;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    boolean visible = false;
    if (project != null && ModuleManager.getInstance(project).getModules().length > 0) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      for (VirtualFile root : getRoots(e)) {
        if (!root.isInLocalFileSystem() && FileUtilRt.extensionEquals(root.getName(), "jar") && !fileIndex.isInLibraryClasses(root)) {
          visible = true;
          break;
        }
        if (root.isInLocalFileSystem() && root.isDirectory()) {
          for (VirtualFile child : root.getChildren()) {
            if (FileUtilRt.extensionEquals(child.getName(), "jar")) {
              final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(child);
              if (jarRoot != null && !fileIndex.isInLibraryClasses(child)) {
                visible = true;
                break;
              }
            }
          }
        }
      }
    }

    e.getPresentation().setEnabledAndVisible(visible);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
