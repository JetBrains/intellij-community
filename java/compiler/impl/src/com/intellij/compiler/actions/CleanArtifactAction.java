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
package com.intellij.compiler.actions;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class CleanArtifactAction extends BuildArtifactActionBase {
  public CleanArtifactAction() {
    super("Clean");
  }

  @Override
  protected String getDescription() {
    return "Output of the selected artifacts will be cleared.";
  }

  @Override
  protected void performAction(Project project, final List<Artifact> artifacts) {
    Set<VirtualFile> parents = new HashSet<VirtualFile>();
    final VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentSourceRoots();
    for (VirtualFile root : roots) {
      VirtualFile parent = root;
      while (parent != null && !parents.contains(parent)) {
        parents.add(parent);
        parent = parent.getParent();
      }
    }

    Map<String, String> outputPathContainingSourceRoots = new HashMap<String, String>();
    final List<File> files = new ArrayList<File>();
    for (Artifact artifact : artifacts) {
      String outputPath = artifact.getOutputFilePath();
      if (outputPath != null) {
        files.add(new File(FileUtil.toSystemDependentName(outputPath)));
        final VirtualFile outputFile = LocalFileSystem.getInstance().findFileByPath(outputPath);
        if (parents.contains(outputFile)) {
          outputPathContainingSourceRoots.put(artifact.getName(), outputPath);
        }
      }
    }

    if (!outputPathContainingSourceRoots.isEmpty()) {
      final String message;
      if (outputPathContainingSourceRoots.size() == 1 && outputPathContainingSourceRoots.values().size() == 1) {
        final String name = ContainerUtil.getFirstItem(outputPathContainingSourceRoots.keySet());
        final String output = outputPathContainingSourceRoots.get(name);
        message = "The output directory '" + output + "' of '" + name + "' artifact contains source roots of the project. Do you want to continue and clear it?";
      }
      else {
        StringBuilder info = new StringBuilder();
        for (String name : outputPathContainingSourceRoots.keySet()) {
          info.append(" '").append(name).append("' artifact ('").append(outputPathContainingSourceRoots.get(name)).append("')\n");
        }
        message = "The output directories of the following artifacts contains source roots:\n" +
                  info + "Do you want to continue and clear these directories?";
      }
      final int answer = Messages.showYesNoDialog(project, message, "Clean Artifacts", null);
      if (answer != 0) {
        return;
      }
    }

    new Task.Backgroundable(project, "Cleaning artifacts...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (File file : files) {
          indicator.checkCanceled();
          FileUtil.delete(file);
        }
        LocalFileSystem.getInstance().refreshIoFiles(files, true, true, null);
      }
    }.queue();
  }
}
