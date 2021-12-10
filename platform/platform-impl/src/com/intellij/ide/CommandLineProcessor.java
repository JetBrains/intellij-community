// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.OpenResult;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil;
import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.idea.CommandLineArgs;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
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
import java.util.concurrent.atomic.AtomicReference;

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
    Project project = null;
    if (!LightEditUtil.isForceOpenInLightEditMode()) {
      OpenResult openResult = ProjectUtil.tryOpenOrImport(file, openProjectOptions);
      if (openResult instanceof OpenResult.Success) {
        project = ((OpenResult.Success)openResult).getProject();
      }
      else if (openResult instanceof OpenResult.Canceled) {
        return CommandLineProcessorResult.createError(IdeBundle.message("dialog.message.open.cancelled"));
      }
    }
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
      if (recentProjectManager.willReopenProjectOnStart() && recentProjectManager.reopenLastProjectsOnStart().join()) {
        projects = ProjectUtil.getOpenProjects();
      }
    }

    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtilRt.toSystemIndependentName(ioFile.toString()));
    if (file == null) {
      if (LightEditUtil.isLightEditEnabled()) {
        Project lightEditProject = LightEditUtil.openFile(ioFile, true);
        if (lightEditProject != null) {
          Future<CliResult> future = shouldWait ? CommandLineWaitingManager.getInstance().addHookForPath(ioFile) : OK_FUTURE;
          return new CommandLineProcessorResult(lightEditProject, future);
        }
      }
      return CommandLineProcessorResult.createError(IdeBundle.message("dialog.message.can.not.open.file", ioFile.toString()));
    }

    if (projects.length == 0) {
      Project project = CommandLineProjectOpenProcessor.getInstance().openProjectAndFile(ioFile, line, column, tempProject);
      if (project == null) {
        return CommandLineProcessorResult.createError(IdeBundle.message("dialog.message.no.project.found.to.open.file.in"));
      }

      return new CommandLineProcessorResult(project, shouldWait ? CommandLineWaitingManager.getInstance().addHookForFile(file) : OK_FUTURE);
    }
    else {
      NonProjectFileWritingAccessProvider.allowWriting(Collections.singletonList(file));
      Project project;
      if (LightEditUtil.isForceOpenInLightEditMode()) {
        project = LightEditService.getInstance().openFile(file);
        LightEditFeatureUsagesUtil.logFileOpen(project, CommandLine);
      }
      else {
        project = findBestProject(file, projects);
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

    IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    if (frame != null) {
      Project project = frame.getProject();
      if (project != null && !LightEdit.owns(project)) {
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

    CommandLineProcessorResult result;
    result = processApplicationStarters(args, currentDirectory);
    if (result != null) return result;

    result = processJetBrainsProtocol(args);
    if (result != null) return result;

    return processOpenFile(args, currentDirectory);
  }

  @Nullable
  private static CommandLineProcessorResult processApplicationStarters(@NotNull List<String> args, @Nullable String currentDirectory) {
    String command = args.get(0);
    return ApplicationStarter.EP_NAME.computeSafeIfAny(starter -> {
      if (!command.equals(starter.getCommandName())) {
        return null;
      }

      if (!starter.canProcessExternalCommandLine()) {
        return CommandLineProcessorResult.createError(IdeBundle.message("dialog.message.only.one.instance.can.be.run.at.time",
                                                                        ApplicationNamesInfo.getInstance().getProductName()));
      }

      LOG.info("Processing command with " + starter);
      int requiredModality = starter.getRequiredModality();
      if (requiredModality == ApplicationStarter.NOT_IN_EDT) {
        return new CommandLineProcessorResult(null, starter.processExternalCommandLineAsync(args, currentDirectory));
      }
      else {
        ModalityState modalityState = requiredModality == ApplicationStarter.ANY_MODALITY
                                      ? ModalityState.any() : ModalityState.defaultModalityState();
        AtomicReference<CommandLineProcessorResult> ref = new AtomicReference<>();
        ApplicationManager.getApplication().invokeAndWait(() -> {
          ref.set(new CommandLineProcessorResult(null, starter.processExternalCommandLineAsync(args, currentDirectory)));
        }, modalityState);
        return ref.get();
      }
    });
  }

  @Nullable
  private static CommandLineProcessorResult processJetBrainsProtocol(@NotNull List<String> args) {
    String command = args.get(0);
    if (command.startsWith(JetBrainsProtocolHandler.PROTOCOL)) {
      JetBrainsProtocolHandler.processJetBrainsLauncherParameters(command);
      ApplicationManager.getApplication().invokeLater(() -> JBProtocolCommand.handleCurrentCommand());
      return new CommandLineProcessorResult(null, OK_FUTURE);
    }
    return null;
  }

  @NotNull
  private static CommandLineProcessorResult processOpenFile(@NotNull List<String> args, @Nullable String currentDirectory) {
    CommandLineProcessorResult projectAndCallback = null;
    int line = -1;
    int column = -1;
    boolean tempProject = false;
    boolean shouldWait = args.contains(OPTION_WAIT);
    boolean lightEditMode = false;

    for (int i = 0; i < args.size(); i++) {
      String arg = args.get(i);
      if (CommandLineArgs.isKnownArgument(arg) || OPTION_WAIT.equals(arg)) {
        continue;
      }

      if (arg.equals("-l") || arg.equals("--line")) {
        //noinspection AssignmentToForLoopParameter
        i++;
        if (i == args.size()) break;
        line = StringUtilRt.parseInt(args.get(i), -1);
        continue;
      }

      if (arg.equals("-c") || arg.equals("--column")) {
        //noinspection AssignmentToForLoopParameter
        i++;
        if (i == args.size()) break;
        column = StringUtilRt.parseInt(args.get(i), -1);
        continue;
      }

      if (arg.equals("--temp-project")) {
        tempProject = true;
        continue;
      }

      if (arg.equals("-e") || arg.equals("--edit")) {
        lightEditMode = true;
        continue;
      }

      if (arg.equals("-p") || arg.equals("--project")) {
        // Skip, replaced with the opposite option above
        // TODO<rv>: Remove in future versions
        continue;
      }

      if (StringUtilRt.isQuotedString(arg)) {
        arg = StringUtilRt.unquoteString(arg);
      }

      Path file = parseFilePath(arg, currentDirectory);
      if (file == null) {
        return CommandLineProcessorResult.createError(IdeBundle.message("dialog.message.invalid.path", arg));
      }

      projectAndCallback = openFileOrProject(file, line, column, tempProject, shouldWait, lightEditMode);

      if (shouldWait) {
        break;
      }

      line = column = -1;
      tempProject = false;
    }

    if (projectAndCallback != null) {
      return projectAndCallback;
    }
    else {
      if (shouldWait) {
        return new CommandLineProcessorResult(
          null,
          CliResult.error(1, IdeBundle.message("dialog.message.wait.must.be.supplied.with.file.or.project.to.wait.for"))
        );
      }

      if (lightEditMode) {
        LightEditService.getInstance().showEditorWindow();
        return new CommandLineProcessorResult(LightEditService.getInstance().getProject(), OK_FUTURE);
      }

      return new CommandLineProcessorResult(null, OK_FUTURE);
    }
  }

  @Nullable
  private static Path parseFilePath(@NotNull String path, @Nullable String currentDirectory) {
    try {
      // handle paths like /file/foo\qwe
      Path file = Paths.get(FileUtilRt.toSystemDependentName(path));
      if (!file.isAbsolute()) {
        file = currentDirectory == null ? file.toAbsolutePath() : Paths.get(currentDirectory).resolve(file);
      }
      return file.normalize();
    }
    catch (InvalidPathException e) {
      LOG.warn(e);
      return null;
    }
  }

  private static @NotNull CommandLineProcessorResult openFileOrProject(@NotNull Path file,
                                                                       int line, int column,
                                                                       boolean tempProject,
                                                                       boolean shouldWait,
                                                                       boolean lightEditMode) {
    return LightEditUtil.computeWithCommandLineOptions(shouldWait, lightEditMode, () -> {
      if (line != -1 || tempProject) {
        return doOpenFile(file, line, column, tempProject, shouldWait);
      }
      return doOpenFileOrProject(file, shouldWait);
    });
  }
}