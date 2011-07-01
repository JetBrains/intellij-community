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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
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

    final List<VirtualFile> jars = getJarRoots(e);
    if (jars.isEmpty()) return;

    new CreateLibraryFromFilesDialog(project, jars).show();
  }

  @NotNull
  private static List<VirtualFile> getJarRoots(AnActionEvent e) {
    final Project project = getEventProject(e);
    final VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (project == null || files == null || files.length == 0) return Collections.emptyList();

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    List<VirtualFile> archives = new ArrayList<VirtualFile>();
    for (VirtualFile file : files) {
      addJarRoot(archives, file, fileIndex);
      if (file.isDirectory()) {
        for (VirtualFile child : file.getChildren()) {
          addJarRoot(archives, child, fileIndex);
        }
      }
    }
    return archives;
  }

  private static void addJarRoot(List<VirtualFile> archives, VirtualFile file, ProjectFileIndex index) {
    final VirtualFile root = JarFileSystem.getInstance().getJarRootForLocalFile(file);
    if (root != null && !index.isInLibraryClasses(root)) {
      archives.add(root);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    boolean visible = !getJarRoots(e).isEmpty();
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible);
  }
}
