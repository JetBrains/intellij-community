/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.projectView.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
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

/**
 * @author nik
 */
public class MarkLibraryRootAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) return;

    final List<VirtualFile> jars = getRoots(e);
    if (jars.isEmpty()) return;

    final List<OrderRoot> roots = RootDetectionUtil.detectRoots(jars, null, project, new DefaultLibraryRootsComponentDescriptor());
    new CreateLibraryFromFilesDialog(project, roots).show();
  }

  @NotNull
  private static List<VirtualFile> getRoots(AnActionEvent e) {
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
  public void update(AnActionEvent e) {
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

    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible);
  }
}
