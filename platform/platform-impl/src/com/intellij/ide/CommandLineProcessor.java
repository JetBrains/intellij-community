// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.idea.SplashManager;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.platform.CommandLineProjectOpenProcessor;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import static com.intellij.ide.CliResult.error;
import static com.intellij.openapi.util.Pair.pair;

/**
 * @author yole
 */
public final class CommandLineProcessor {
  private static final Logger LOG = Logger.getInstance(CommandLineProcessor.class);
  private static final String OPTION_WAIT = "--wait";

  private CommandLineProcessor() { }

  private static @NotNull Pair<Project, Future<CliResult>> doOpenFileOrProject(Path file, boolean shouldWait) {
    OpenProjectTask openProjectOptions = new OpenProjectTask();
    // do not check for .ipr files in specified directory (@develar: it is existing behaviour, I am not fully sure that it is correct)
    openProjectOptions.checkDirectoryForFileBasedProjects = false;
    Project project = ProjectUtil.openOrImport(file, openProjectOptions);
    if (project == null) {
      return doOpenFile(file, -1, -1, false, shouldWait);
    }
    else {
      return pair(project, shouldWait ? CommandLineWaitingManager.getInstance().addHookForProject(project) : CliResult.OK_FUTURE);
    }
  }

  private static @NotNull Pair<Project, Future<CliResult>> doOpenFile(Path ioFile, int line, int column, boolean tempProject, boolean shouldWait) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(ioFile.toString()));
    assert file != null;

    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    if (projects.length == 0 || tempProject) {
      Project project = CommandLineProjectOpenProcessor.getInstance().openProjectAndFile(file, line, column, tempProject);
      if (project == null) {
        final String message = "No project found to open file in";
        Messages.showErrorDialog(message, "Cannot Open File");
        return pair(null, error(1, message));
      }

      return pair(project, shouldWait ? CommandLineWaitingManager.getInstance().addHookForFile(file) : CliResult.OK_FUTURE);
    }
    else {
      NonProjectFileWritingAccessProvider.allowWriting(Collections.singletonList(file));
      Project project = findBestProject(file, projects);
      Navigatable navigatable = line > 0
        ? new OpenFileDescriptor(project, file, line - 1, Math.max(column, 0))
        : PsiNavigationSupport.getInstance().createNavigatable(project, file, -1);
      navigatable.navigate(true);

      return pair(project, shouldWait ? CommandLineWaitingManager.getInstance().addHookForFile(file) : CliResult.OK_FUTURE);
    }
  }

  private static @NotNull Project findBestProject(VirtualFile file, Project[] projects) {
    for (Project project : projects) {
      if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(file)) {
        return project;
      }
    }

    IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    if (frame != null) {
      Project project = frame.getProject();
      if (project != null) {
        return project;
      }
    }

    return projects[0];
  }

  public static @NotNull Pair<Project, Future<CliResult>> processExternalCommandLine(@NotNull List<String> args, @Nullable String currentDirectory) {
    LOG.info("External command line:");
    LOG.info("Dir: " + currentDirectory);
    for (String arg : args) LOG.info(arg);
    LOG.info("-----");
    if (args.isEmpty()) return pair(null, CliResult.OK_FUTURE);

    String command = args.get(0);
    Pair<Project, Future<CliResult>> result = ApplicationStarter.EP_NAME.computeSafeIfAny(starter -> {
      if (!command.equals(starter.getCommandName())) {
        return null;
      }

      if (starter.canProcessExternalCommandLine()) {
        LOG.info("Processing command with " + starter);
        return pair(null, starter.processExternalCommandLineAsync(args, currentDirectory));
      }
      else {
        String title = "Cannot execute command '" + command + "'";
        String message = "Only one instance of " + ApplicationNamesInfo.getInstance().getProductName() + " can be run at a time.";
        Messages.showErrorDialog(message, title);
        return pair(null, error(1, message));
      }
    });
    if (result != null) {
      return result;
    }

    if (command.startsWith(JetBrainsProtocolHandler.PROTOCOL)) {
      JetBrainsProtocolHandler.processJetBrainsLauncherParameters(command);
      ApplicationManager.getApplication().invokeLater(() -> JBProtocolCommand.handleCurrentCommand());
      return pair(null, CliResult.OK_FUTURE);
    }

    Pair<Project, Future<CliResult>> projectAndCallback = null;
    int line = -1;
    int column = -1;
    boolean tempProject = false;
    boolean shouldWait = args.contains(OPTION_WAIT);

    for (int i = 0; i < args.size(); i++) {
      String arg = args.get(i);
      if (SplashManager.NO_SPLASH.equals(arg) || OPTION_WAIT.equals(arg)) {
        continue;
      }

      if (arg.equals("-l") || arg.equals("--line")) {
        //noinspection AssignmentToForLoopParameter
        i++;
        if (i == args.size()) break;
        line = StringUtil.parseInt(args.get(i), -1);
        continue;
      }

      if (arg.equals("-c") || arg.equals("--column")) {
        //noinspection AssignmentToForLoopParameter
        i++;
        if (i == args.size()) break;
        column = StringUtil.parseInt(args.get(i), -1);
        continue;
      }

      if (arg.equals("--temp-project")) {
        tempProject = true;
        continue;
      }

      if (StringUtil.isQuotedString(arg)) {
        arg = StringUtil.unquoteString(arg);
      }

      Path file = Paths.get(arg);
      if (!file.isAbsolute()) {
        file = currentDirectory == null ? file.toAbsolutePath() : Paths.get(currentDirectory).resolve(file);
      }

      if (!Files.exists(file)) {
        String message = "Cannot find file '" + file + "'";
        Messages.showErrorDialog(message, "Cannot Find File");
        return pair(null, error(1, message));
      }

      if (line != -1 || tempProject) {
        projectAndCallback = doOpenFile(file, line, column, tempProject, shouldWait);
      }
      else {
        projectAndCallback = doOpenFileOrProject(file, shouldWait);
      }

      if (shouldWait) {
        break;
      }

      line = column = -1;
      tempProject = false;
    }

    if (shouldWait && projectAndCallback == null) {
      return pair(null, error(1, "--wait must be supplied with file or project to wait for"));
    }

    return projectAndCallback == null ? pair(null, CliResult.OK_FUTURE) : projectAndCallback;
  }
}