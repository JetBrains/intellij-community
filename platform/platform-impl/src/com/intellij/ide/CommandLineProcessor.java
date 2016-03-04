/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.idea.StartupUtil;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;

/**
 * @author yole
 */
public class CommandLineProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.CommandLineProcessor");

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
      return doOpenFile(virtualFile, -1);
    }
  }

  @Nullable
  private static Project doOpenFile(VirtualFile virtualFile, int line) {
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    if (projects.length == 0) {
      final PlatformProjectOpenProcessor processor = PlatformProjectOpenProcessor.getInstanceIfItExists();
      if (processor != null) {
        return PlatformProjectOpenProcessor.doOpenProject(virtualFile, null, false, line, null, false);
      }
      Messages.showErrorDialog("No project found to open file in", "Cannot open file");
      return null;
    }
    else {
      Project project = findBestProject(virtualFile, projects);
      if (line == -1) {
        new OpenFileDescriptor(project, virtualFile).navigate(true);
      }
      else {
        new OpenFileDescriptor(project, virtualFile, line-1, 0).navigate(true);
      }
      return project;
    }
  }

  @NotNull
  private static Project findBestProject(VirtualFile virtualFile, Project[] projects) {
    for (Project aProject : projects) {
      if (ProjectRootManager.getInstance(aProject).getFileIndex().isInContent(virtualFile)) {
        return aProject;
      }
    }
    IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    Project project = frame == null ? null : frame.getProject();
    return project != null ? project : projects[0];
  }

  @Nullable
  public static Project processExternalCommandLine(List<String> args, @Nullable String currentDirectory) {
    if (args.size() > 0) {
      LOG.info("External command line:");
      LOG.info("Dir: " + currentDirectory);
      for (String arg : args) {
        LOG.info(arg);
      }
    }
    LOG.info("-----");

    if (args.size() > 0) {
      final String command = args.get(0);
      for(ApplicationStarter starter: Extensions.getExtensions(ApplicationStarter.EP_NAME)) {
        if (command.equals(starter.getCommandName()) &&
            starter instanceof ApplicationStarterEx &&
            ((ApplicationStarterEx)starter).canProcessExternalCommandLine()) {
          LOG.info("Processing command with " + starter);
          ((ApplicationStarterEx) starter).processExternalCommandLine(ArrayUtil.toStringArray(args), currentDirectory);
          return null;
        }
      }

      if (command.startsWith(JetBrainsProtocolHandler.PROTOCOL)) {
        try {
          final String url = URLDecoder.decode(command, "UTF-8");
          JetBrainsProtocolHandler.processJetBrainsLauncherParameters(url);
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              JBProtocolCommand.handleCurrentCommand();
            }
          }, ModalityState.any());
        }
        catch (UnsupportedEncodingException e) {
          LOG.error(e);
        }

        return null;
      }
    }

    Project lastOpenedProject = null;
    int line = -1;
    for (int i = 0, argsSize = args.size(); i < argsSize; i++) {
      String arg = args.get(i);
      if (arg.equals(StartupUtil.NO_SPLASH)) {
        continue;
      }
      if (arg.equals("-l") || arg.equals("--line")) {
        //noinspection AssignmentToForLoopParameter
        i++;
        if (i == args.size()) {
          break;
        }
        try {
          line = Integer.parseInt(args.get(i));
        }
        catch (NumberFormatException e) {
          line = -1;
        }
      }
      else {
        if (StringUtil.isQuotedString(arg)) {
          arg = StringUtil.stripQuotesAroundValue(arg);
        }
        if (!new File(arg).isAbsolute()) {
          arg = currentDirectory != null ? new File(currentDirectory, arg).getAbsolutePath() : new File(arg).getAbsolutePath();
        }
        if (line != -1) {
          final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(arg);
          if (virtualFile != null) {
            lastOpenedProject = doOpenFile(virtualFile, line);
          }
          else {
            Messages.showErrorDialog("Cannot find file '" + arg + "'", "Cannot find file");
          }
        }
        else {
          lastOpenedProject = doOpenFileOrProject(arg);
        }
      }
    }
    return lastOpenedProject;
  }
}
