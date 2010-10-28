/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;

/**
 * @author yole
 */
public class CommandLineProcessor {
  private CommandLineProcessor() {
  }

  public static void openFileOrProject(final String name) {
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (name != null) {
          doOpenFileOrProject(name);
        }
      }
    });
  }

  @Nullable
  private static Project doOpenFileOrProject(String name) {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(name);
    if (virtualFile == null) {
      Messages.showErrorDialog("Cannot find file '" + name + "'", "Cannot find file");
      return null;
    }
    ProjectOpenProcessor provider = ProjectOpenProcessor.getImportProvider(virtualFile);
    if (provider instanceof PlatformProjectOpenProcessor && !virtualFile.isDirectory()) {
      // HACK: PlatformProjectOpenProcessor agrees to open anything
      provider = null;
    }
    if (provider != null ||
        name.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION) ||
        new File(name, Project.DIRECTORY_STORE_FOLDER).exists()) {
      final Project result = ProjectUtil.openOrImport(name, null, true);
      if (result == null) {
        Messages.showErrorDialog("Cannot open project '" + name + "'", "Cannot open project");
      }
      return result;
    }
    else {
      final Project[] projects = ProjectManager.getInstance().getOpenProjects();
      if (projects.length == 0) {
        Messages.showErrorDialog("No project found to open file in", "Cannot open file");
        return null;
      }
      else {
        // TODO[yole]: search for project which has specified file under content root
        final Project project = projects[0];
        new OpenFileDescriptor(project, virtualFile).navigate(true);
        return project;
      }
    }
  }

  @Nullable
  public static Project processExternalCommandLine(List<String> args) {
    // TODO[yole] handle AppStarters here?
    Project lastOpenedProject = null;
    for (String arg : args) {
      lastOpenedProject = doOpenFileOrProject(arg);
    }
    return lastOpenedProject;
  }
}
