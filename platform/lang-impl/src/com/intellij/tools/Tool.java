// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tools;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.execution.wsl.WSLCommandLineOptions;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.ide.macro.Macro;
import com.intellij.ide.macro.MacroManager;
import com.intellij.ide.macro.MacroPathConverter;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.SchemeElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.platform.eel.provider.utils.JEelUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Tool implements SchemeElement {
  private static final Logger LOG = Logger.getInstance(Tool.class);

  public static final @NonNls String ACTION_ID_PREFIX = "Tool_";

  public static final @Nls String DEFAULT_GROUP_NAME = ToolsBundle.message("external.tools");
  protected static final ProcessEvent NOT_STARTED_EVENT = new ProcessEvent(new NopProcessHandler(), -1);
  private @NlsSafe String myName;
  private String myDescription;
  private @NotNull String myGroup = DEFAULT_GROUP_NAME;

  // These 4 fields and everything related are effectively not used anymore, see IDEA-190856.
  // Let's keep them for a while for compatibility in case we have to reconsider.
  private boolean myShownInMainMenu;
  private boolean myShownInEditor;
  private boolean myShownInProjectViews;
  private boolean myShownInSearchResultsPopup;

  private boolean myEnabled;

  private boolean myUseConsole;
  private boolean myShowConsoleOnStdOut;
  private boolean myShowConsoleOnStdErr;
  private boolean mySynchronizeAfterExecution;

  private String myWorkingDirectory;
  private String myProgram;
  private String myParameters;

  private ArrayList<FilterInfo> myOutputFilters = new ArrayList<>();

  public Tool() {
  }

  public @NlsSafe String getName() {
    return myName;
  }

  public @NlsSafe String getDescription() {
    return myDescription;
  }

  public @NlsSafe @NotNull String getGroup() {
    return myGroup;
  }

  public boolean isShownInMainMenu() {
    return myShownInMainMenu;
  }

  public boolean isShownInEditor() {
    return myShownInEditor;
  }

  public boolean isShownInProjectViews() {
    return myShownInProjectViews;
  }

  public boolean isShownInSearchResultsPopup() {
    return myShownInSearchResultsPopup;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public boolean isUseConsole() {
    return myUseConsole;
  }

  public boolean isShowConsoleOnStdOut() {
    return myShowConsoleOnStdOut;
  }

  public boolean isShowConsoleOnStdErr() {
    return myShowConsoleOnStdErr;
  }

  public boolean synchronizeAfterExecution() {
    return mySynchronizeAfterExecution;
  }

  public void setName(@NlsSafe String name) {
    myName = name;
  }

  public void setDescription(@NlsSafe String description) {
    myDescription = description;
  }

  public void setGroup(@NonNls @NotNull String group) {
    myGroup = StringUtil.isEmpty(group) ? DEFAULT_GROUP_NAME : group;
  }

  public void setShownInMainMenu(boolean shownInMainMenu) {
    myShownInMainMenu = shownInMainMenu;
  }

  public void setShownInEditor(boolean shownInEditor) {
    myShownInEditor = shownInEditor;
  }

  public void setShownInProjectViews(boolean shownInProjectViews) {
    myShownInProjectViews = shownInProjectViews;
  }

  public void setShownInSearchResultsPopup(boolean shownInSearchResultsPopup) {
    myShownInSearchResultsPopup = shownInSearchResultsPopup;
  }

  public void setUseConsole(boolean useConsole) {
    myUseConsole = useConsole;
  }

  public void setShowConsoleOnStdOut(boolean showConsole) {
    myShowConsoleOnStdOut = showConsole;
  }

  public void setShowConsoleOnStdErr(boolean showConsole) {
    myShowConsoleOnStdErr = showConsole;
  }

  public void setFilesSynchronizedAfterRun(boolean synchronizeAfterRun) {
    mySynchronizeAfterExecution = synchronizeAfterRun;
  }

  public String getWorkingDirectory() {
    return myWorkingDirectory;
  }

  public void setWorkingDirectory(String workingDirectory) {
    myWorkingDirectory = workingDirectory;
  }

  public String getProgram() {
    return myProgram;
  }

  public void setProgram(String program) {
    myProgram = program;
  }

  public String getParameters() {
    return myParameters;
  }

  public void setParameters(String parameters) {
    myParameters = parameters;
  }

  public void addOutputFilter(FilterInfo filter) {
    myOutputFilters.add(filter);
  }

  public void setOutputFilters(FilterInfo[] filters) {
    myOutputFilters = new ArrayList<>();
    if (filters != null) {
      Collections.addAll(myOutputFilters, filters);
    }
  }

  public FilterInfo[] getOutputFilters() {
    return myOutputFilters.toArray(new FilterInfo[0]);
  }

  public void copyFrom(Tool source) {
    myName = source.getName();
    myDescription = source.getDescription();
    myGroup = source.getGroup();
    myShownInMainMenu = source.isShownInMainMenu();
    myShownInEditor = source.isShownInEditor();
    myShownInProjectViews = source.isShownInProjectViews();
    myShownInSearchResultsPopup = source.isShownInSearchResultsPopup();
    myEnabled = source.isEnabled();
    myUseConsole = source.isUseConsole();
    myShowConsoleOnStdOut = source.isShowConsoleOnStdOut();
    myShowConsoleOnStdErr = source.isShowConsoleOnStdErr();
    mySynchronizeAfterExecution = source.synchronizeAfterExecution();
    myWorkingDirectory = source.getWorkingDirectory();
    myProgram = source.getProgram();
    myParameters = source.getParameters();
    myOutputFilters = new ArrayList<>(Arrays.asList(source.getOutputFilters()));
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Tool source)) {
      return false;
    }

    return
      Objects.equals(myName, source.myName) &&
      Objects.equals(myDescription, source.myDescription) &&
      Objects.equals(myGroup, source.myGroup) &&
      myShownInMainMenu == source.myShownInMainMenu &&
      myShownInEditor == source.myShownInEditor &&
      myShownInProjectViews == source.myShownInProjectViews &&
      myShownInSearchResultsPopup == source.myShownInSearchResultsPopup &&
      myEnabled == source.myEnabled &&
      myUseConsole == source.myUseConsole &&
      myShowConsoleOnStdOut == source.myShowConsoleOnStdOut &&
      myShowConsoleOnStdErr == source.myShowConsoleOnStdErr &&
      mySynchronizeAfterExecution == source.mySynchronizeAfterExecution &&
      Objects.equals(myWorkingDirectory, source.myWorkingDirectory) &&
      Objects.equals(myProgram, source.myProgram) &&
      Objects.equals(myParameters, source.myParameters) &&
      Comparing.equal(myOutputFilters, source.myOutputFilters);
  }

  public @NotNull String getActionId() {
    StringBuilder name = new StringBuilder(getActionIdPrefix());
    name.append(myGroup);
    name.append('_');
    if (myName != null) {
      name.append(myName);
    }
    return name.toString();
  }

  protected static void notifyCouldNotStart(@Nullable ProcessListener listener) {
    if (listener != null) listener.processTerminated(NOT_STARTED_EVENT);
  }

  public void execute(AnActionEvent event, DataContext dataContext, long executionId, final @Nullable ProcessListener processListener) {
    if (!executeIfPossible(event, dataContext, executionId, processListener)) {
      notifyCouldNotStart(processListener);
    }
  }

  public boolean executeIfPossible(AnActionEvent event,
                                   DataContext dataContext,
                                   long executionId,
                                   final @Nullable ProcessListener processListener) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }

    FileDocumentManager.getInstance().saveAllDocuments();
    try {
      if (isUseConsole()) {
        ExecutionEnvironment environment = ExecutionEnvironmentBuilder.create(project,
                                                                              DefaultRunExecutor.getRunExecutorInstance(),
                                                                              new ToolRunProfile(this, dataContext))
          .build(new ProgramRunner.Callback() {
            @Override
            public void processStarted(RunContentDescriptor descriptor) {
              ProcessHandler processHandler = descriptor.getProcessHandler();
              if (processHandler != null && processListener != null) {
                LOG.assertTrue(!processHandler.isStartNotified(),
                               "ProcessHandler is already startNotified, the listener won't be correctly notified");
                processHandler.addProcessListener(processListener);
              }
            }

            @Override
            public void processNotStarted(@Nullable Throwable error) {
              if (processListener != null) {
                processListener.processNotStarted();
              }
            }
          });
        if (environment.getState() == null) {
          return false;
        }

        environment.setExecutionId(executionId);
        environment.getRunner().execute(environment);
      }
      else {
        GeneralCommandLine commandLine = createCommandLine(dataContext);
        if (commandLine == null) {
          return false;
        }
        OSProcessHandler handler =
          UtilKt.runToolOnBgtWithModality(project, commandLine, myName != null ? myName : commandLine.getExePath());
        handler.addProcessListener(new ToolProcessAdapter(project, synchronizeAfterExecution(), getName()));
        if (processListener != null) {
          handler.addProcessListener(processListener);
        }
        handler.startNotify();
      }
    }
    catch (ExecutionException ex) {
      ExecutionErrorDialog.show(ex, ToolsBundle.message("tools.process.start.error"), project);
      notifyCouldNotStart(processListener);
      return false;
    }
    return true;
  }

  public @Nullable GeneralCommandLine createCommandLine(DataContext dataContext) {
    if (StringUtil.isEmpty(getWorkingDirectory())) {
      setWorkingDirectory("$ProjectFileDir$");
    }

    GeneralCommandLine commandLine = Registry.is("use.tty.for.external.tools", false)
                                     ? new PtyCommandLine().withConsoleMode(true)
                                     : new GeneralCommandLine();
    try {
      String exePathStr = MacroManager.getInstance().expandMacrosInString(getProgram(), true, dataContext);
      exePathStr = MacroManager.getInstance().expandMacrosInString(exePathStr, false, dataContext);
      if (exePathStr == null) return null;

      String workingDir = MacroManager.getInstance().expandMacrosInString(getWorkingDirectory(), true, dataContext);
      final String workDirExpanded = MacroManager.getInstance().expandMacrosInString(workingDir, false, dataContext);
      final var workDirPath = !StringUtil.isEmpty(workDirExpanded) ? Path.of(workDirExpanded) : null;
      if (workDirPath != null) {
        commandLine.withWorkingDirectory(workDirPath);
      }

      Path exePath = Path.of(exePathStr);
      DataContext paramContext = SimpleDataContext
        .builder()
        .add(MacroManager.PATH_CONVERTER_KEY, new EelMacroPathConverter())
        .add(MacroManager.CONTEXT_PATH, getContextPath(exePath, workDirPath))
        .setParent(dataContext)
        .build();

      String paramString = MacroManager.getInstance().expandMacrosInString(getParameters(), true, paramContext);

      commandLine.getParametersList().addParametersString(
        MacroManager.getInstance().expandMacrosInString(paramString, false, paramContext));

      if (Files.isDirectory(exePath) && exePath.getFileName().endsWith(".app")) {
        commandLine.withExePath("open");
        commandLine.getParametersList().prependAll("-a", exePath.toString());
      }
      else {
        var eelPath = JEelUtils.toEelPath(exePath);
        commandLine.withExePath(eelPath != null ? eelPath.toString() : exePathStr);
      }
    }
    catch (Macro.ExecutionCancelledException ignored) {
      return null;
    }
    return ToolsCustomizer.customizeCommandLine(commandLine, dataContext);
  }

  @Override
  public void setGroupName(final @NotNull String name) {
    setGroup(name);
  }

  @Override
  public String getKey() {
    return getName();
  }

  @Override
  public @NotNull SchemeElement copy() {
    Tool copy = new Tool();
    copy.copyFrom(this);
    return copy;
  }

  @Override
  public String toString() {
    return myGroup + ": " + myName;
  }

  public String getActionIdPrefix() {
    return ACTION_ID_PREFIX;
  }

  /**
   * @deprecated Consider using EelAPI, this method is not needed then
   */
  @Deprecated
  public static @NotNull GeneralCommandLine createWslCommandLine(@Nullable Project project,
                                                                 @NotNull WSLDistribution wsl,
                                                                 @NotNull GeneralCommandLine cmd,
                                                                 @Nullable String linuxWorkingDir,
                                                                 @NotNull String linuxExePath) throws ExecutionException {
    cmd.setExePath(linuxExePath);
    WSLCommandLineOptions wslOptions = new WSLCommandLineOptions();
    if (StringUtil.isNotEmpty(linuxWorkingDir)) {
      wslOptions.setRemoteWorkingDirectory(linuxWorkingDir);
    }
    // Working directory as well as all parameters were computed with MacroPathConverter, so they are
    // paths in linux. Reset working directory in command line, because linux directory is not valid
    // in windows, and we will fail to start process with it.
    cmd.setWorkDirectory((String)null);
    // run command in interactive shell so that shell rc files are executed and configure proper environment
    wslOptions.setExecuteCommandInInteractiveShell(true);
    return wsl.patchCommandLine(cmd, project, wslOptions);
  }

  private static class EelMacroPathConverter implements MacroPathConverter {

    @Override
    public @NotNull String convertPath(@NotNull String path) {
      if (!path.isEmpty()) {
        var eelPath = JEelUtils.toEelPath(Path.of(path));
        if (eelPath != null) return eelPath.toString();
      }
      return path;
    }

    @Override
    public @NotNull String convertPathList(@NotNull String pathList) {
      List<String> paths = StringUtil.split(pathList, File.pathSeparator);
      return Strings.join(ContainerUtil.map(paths, p -> convertPath(p)), ":");
    }
  }

  private static @Nullable Path getContextPath(@NotNull Path cmd, @Nullable Path workDir) {
    if(JEelUtils.toEelPath(cmd) != null) return cmd;
    if (workDir != null && JEelUtils.toEelPath(workDir) != null) return workDir;
    return null;
  }
}
