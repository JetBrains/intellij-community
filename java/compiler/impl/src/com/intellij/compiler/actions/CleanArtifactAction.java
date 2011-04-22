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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
    final List<File> files = new ArrayList<File>();
    for (Artifact artifact : artifacts) {
      String outputPath = artifact.getOutputFilePath();
      if (outputPath != null) {
        files.add(new File(FileUtil.toSystemDependentName(outputPath)));
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
