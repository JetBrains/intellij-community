// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil;
import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.idea.CommandLineArgs;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.platform.CommandLineProjectOpenProcessor;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.pom.Navigatable;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.OpenPlace.CommandLine;

public final class CommandLineProcessor {
  private static final Logger LOG = Logger.getInstance(CommandLineProcessor.class);
  private static final String OPTION_WAIT = "--wait";
  public static final Future<CliResult> OK_FUTURE = CompletableFuture.completedFuture(CliResult.OK);

  private CommandLineProcessor() { }

  // public for testing
  @ApiStatus.Internal
  public static @NotNull CommandLineProcessorResult doOpenFileOrProject(@NotNull Path file, boolean shouldWait) {
    OpenProjectTask openProjectOptions = PlatformProjectOpenProcessor.createOptionsToOpenDotIdeaOrCreateNewIfNotExists(file, null);
    // do not check for .ipr files in specified directory (@develar: it is existing behaviour, I am not fully sure that it is correct)
    openProjectOptions.checkDirectoryForFileBasedProjects = false;
    Project project = ProjectUtil.openOrImport(file, openProjectOptions);
    if (project == null) {
      return doOpenFile(file, -1, -1, false, shouldWait);
    }
    else {
      return new CommandLineProcessorResult(project, shouldWait ? CommandLineWaitingManager.getInstance().addHookForProject(project) : OK_FUTURE);
    }
  }

  private static @NotNull CommandLineProcessorResult doOpenFile(@NotNull Path ioFile, int line, int column, boolean tempProject, boolean shouldWait) {
    Project[] projects = tempProject ? new Project[0] : ProjectUtil.getOpenProjects();
    if (!tempProject && projects.length == 0 && PlatformUtils.isDataGrip()) {
      RecentProjectsManager recentProjectManager = RecentProjectsManager.getInstance();
      if (recentProjectManager.willReopenProjectOnStart() && recentProjectManager.reopenLastProjectsOnStart()) {
        projects = ProjectUtil.getOpenProjects();
      }
    }

    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(ioFile.toString()));
    if (file == null) {
      if (LightEditUtil.openFile(ioFile)) {
        return new CommandLineProcessorResult(LightEditUtil.getProject(), OK_FUTURE);
      }
      else {
        return CommandLineProcessorResult.createError("Can not open file " + ioFile.toString());
      }
    }

    if (projects.length == 0) {
      Project project = CommandLineProjectOpenProcessor.getInstance().openProjectAndFile(ioFile, line, column, tempProject);
      if (project == null) {
        return CommandLineProcessorResult.createError("No project found to open file in");
      }

      return new CommandLineProcessorResult(project, shouldWait ? CommandLineWaitingManager.getInstance().addHookForFile(file) : OK_FUTURE);
    }
    else {
      NonProjectFileWritingAccessProvider.allowWriting(Collections.singletonList(file));
      Project project = findBestProject(file, projects);
      if (LightEdit.owns(project)) {
        if (LightEdit.openFile(file)) {
          LightEditFeatureUsagesUtil.logFileOpen(CommandLine);
        }
      }
      else {
        Navigatable navigatable = line > 0
                                  ? new OpenFileDescriptor(project, file, line - 1, Math.max(column, 0))
                                  : PsiNavigationSupport.getInstance().createNavigatable(project, file, -1);
        AppUIExecutor.onUiThread().expireWith(project).execute(() -> {
          navigatable.navigate(true);
        });
      }

      return new CommandLineProcessorResult(project, shouldWait ? CommandLineWaitingManager.getInstance().addHookForFile(file) : OK_FUTURE);
    }
  }

  private static @NotNull Project findBestProject(@NotNull VirtualFile file, @NotNull Project @NotNull[] projects) {
    for (Project project : projects) {
      ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
      if (ReadAction.compute(() -> fileIndex.isInContent(file))) {
        return project;
      }
    }

    if (LightEditUtil.isLightEditEnabled() && !LightEditUtil.isOpenInExistingProject()) {
      return LightEditUtil.getProject();
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

  public static @NotNull CommandLineProcessorResult processExternalCommandLine(@NotNull List<String> args, @Nullable String currentDirectory) {
    StringBuilder logMessage = new StringBuilder();
    logMessage.append("External command line:").append('\n');
    logMessage.append("Dir: ").append(currentDirectory).append('\n');
    for (String arg : args) {
      logMessage.append(arg).append('\n');
    }
    logMessage.append("-----");
    LOG.info(logMessage.toString());
    if (args.isEmpty()) {
      return new CommandLineProcessorResult(null, OK_FUTURE);
    }

    String command = args.get(0);
    CommandLineProcessorResult result = ApplicationStarter.EP_NAME.computeSafeIfAny(starter -> {
      if (!command.equals(starter.getCommandName())) {
        return null;
      }

      if (starter.canProcessExternalCommandLine()) {
        LOG.info("Processing command with " + starter);
        return new CommandLineProcessorResult(null, starter.processExternalCommandLineAsync(args, currentDirectory));
      }
      else {
        return CommandLineProcessorResult.createError("Only one instance of " + ApplicationNamesInfo.getInstance().getProductName() + " can be run at a time.");
      }
    });
    if (result != null) {
      return result;
    }

    if (command.startsWith(JetBrainsProtocolHandler.PROTOCOL)) {
      JetBrainsProtocolHandler.processJetBrainsLauncherParameters(command);
      ApplicationManager.getApplication().invokeLater(() -> JBProtocolCommand.handleCurrentCommand());
      return new CommandLineProcessorResult(null, OK_FUTURE);
    }

    CommandLineProcessorResult projectAndCallback = null;
    int line = -1;
    int column = -1;
    boolean tempProject = false;
    boolean shouldWait = args.contains(OPTION_WAIT);
    LightEditUtil.setForceOpenInExistingProject(false);

    for (int i = 0; i < args.size(); i++) {
      String arg = args.get(i);
      if (CommandLineArgs.isKnownArgument(arg) || OPTION_WAIT.equals(arg)) {
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

      if (arg.equals("-p") || arg.equals("--project")) {
        LightEditUtil.setForceOpenInExistingProject(true);
        continue;
      }

      if (StringUtil.isQuotedString(arg)) {
        arg = StringUtil.unquoteString(arg);
      }

      Path file = null;
      try {
        file = Paths.get(arg);
        if (!file.isAbsolute()) {
          file = currentDirectory == null ? file.toAbsolutePath() : Paths.get(currentDirectory).resolve(file);
        }
        file = file.normalize();
      }
      catch (InvalidPathException e) {
        LOG.warn(e);
      }
      if (file == null) {
        return CommandLineProcessorResult.createError("Invalid path '" + arg + "'");
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
      return new CommandLineProcessorResult(null, CliResult.error(1, "--wait must be supplied with file or project to wait for"));
    }

    return projectAndCallback == null ? new CommandLineProcessorResult(null, OK_FUTURE) : projectAndCallback;
  }
}