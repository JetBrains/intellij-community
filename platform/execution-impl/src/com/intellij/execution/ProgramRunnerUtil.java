// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.InvalidRunConfigurationIcon;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.io.File;
import java.util.function.Function;

public final class ProgramRunnerUtil {
  private static final Logger LOG = Logger.getInstance(ProgramRunnerUtil.class);

  private ProgramRunnerUtil() { }

  /**
   * @deprecated Use {@link ProgramRunner#getRunner(String, RunProfile)}
   */
  @Nullable
  @Deprecated
  public static ProgramRunner<?> getRunner(@NotNull String executorId, @Nullable RunnerAndConfigurationSettings configuration) {
    return configuration == null ? null : ProgramRunner.getRunner(executorId, configuration.getConfiguration());
  }

  public static void executeConfiguration(@NotNull ExecutionEnvironment environment, boolean showSettings, boolean assignNewId) {
    executeConfigurationAsync(environment, showSettings, assignNewId, null);
  }

  @NotNull
  public static @NlsContexts.DialogMessage String getCannotRunOnErrorMessage(@NotNull RunProfile profile, @NotNull ExecutionTarget target) {
    return StringUtil.escapeXmlEntities(ExecutionBundle.message("dialog.message.cannot.run.profile.on.target", profile.getName(), target.getDisplayName()));
  }

  public static void executeConfigurationAsync(@NotNull ExecutionEnvironment environment,
                                               boolean showSettings,
                                               boolean assignNewId,
                                               @Nullable ProgramRunner.Callback callback) {
    ExecutionManagerImpl manager = (ExecutionManagerImpl)ExecutionManager.getInstance(environment.getProject());
    if (!manager.isStarting(environment)) {
      if (callback != null) {
        environment.setCallback(callback);
      }
      manager.executeConfiguration(environment, showSettings, assignNewId);
    }
  }

  public static void handleExecutionError(Project project,
                                          @NotNull ExecutionEnvironment environment,
                                          Throwable e,
                                          RunProfile configuration) {
    String name = configuration != null ? configuration.getName() : environment.getRunProfile().getName();
    String windowId = RunContentManager.getInstance(project).getToolWindowIdByEnvironment(environment);
    if (configuration instanceof ConfigurationWithCommandLineShortener && ExecutionUtil.isProcessNotCreated(e)) {
      handleProcessNotStartedError((ConfigurationWithCommandLineShortener)configuration, (ProcessNotCreatedException)e, name, windowId, environment);
    }
    else {
      ExecutionUtil.handleExecutionError(project, windowId, name, e);
    }
  }

  private static void handleProcessNotStartedError(@NotNull ConfigurationWithCommandLineShortener configuration,
                                                   @NotNull ProcessNotCreatedException e,
                                                   String name,
                                                   String windowId,
                                                   @NotNull ExecutionEnvironment environment) {
    String description = e.getMessage();
    HyperlinkListener listener = null;
    Project project = configuration.getProject();
    RunManager runManager = RunManager.getInstance(project);
    RunnerAndConfigurationSettings runnerAndConfigurationSettings = ContainerUtil.find(runManager.getAllSettings(), settings -> settings.getConfiguration() == configuration);
    if (runnerAndConfigurationSettings != null && noShortenerConfigured(configuration)) {
      ConfigurationFactory factory = runnerAndConfigurationSettings.getFactory();
      RunnerAndConfigurationSettings configurationTemplate = runManager.getConfigurationTemplate(factory);
      ConfigurationWithCommandLineShortener templateConfiguration = (ConfigurationWithCommandLineShortener)configurationTemplate.getConfiguration();
      description = ExecutionBundle.message("dialog.message.command.line.too.long", name);
      
      String exePath = e.getCommandLine().getExePath();
      JdkVersionDetector.JdkVersionInfo jdkVersionInfo = JdkVersionDetector.getInstance().detectJdkVersionInfo(new File(exePath).getParentFile().getParent());
      if (jdkVersionInfo != null) {
        description += "<br/>";
        description += ExecutionBundle.message(jdkVersionInfo.version.feature >= 9 ? "dialog.message.command.line.too.long.java9" 
                                                                                   : "dialog.message.command.line.too.long.java8");
      }

      listener = event -> {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          String eventDescription = event.getDescription();
          if ("edit".equals(eventDescription)) {
            RunDialog.editConfiguration(project, 
                                        runnerAndConfigurationSettings, 
                                        ExecutionBundle.message("edit.run.configuration.for.item.dialog.title", name));
          }
          else if (eventDescription != null) {
            ShortenCommandLine shortener = getShortenerFromLink(eventDescription);
            if (shortener != null) {
              configuration.setShortenCommandLine(shortener);
              if (noShortenerConfigured(templateConfiguration)) {
                templateConfiguration.setShortenCommandLine(shortener);
              }
            }
            ExecutionUtil.restart(environment);
          }
        }
      };
    }
    ExecutionUtil.handleExecutionError(project, windowId, e, ExecutionBundle.message("error.running.configuration.message", name), description, Function.identity(), listener);
  }

  private static ShortenCommandLine getShortenerFromLink(@NotNull String eventDescription) {
    return switch (eventDescription) {
      case "args" -> ShortenCommandLine.ARGS_FILE;
      case "jar" -> ShortenCommandLine.MANIFEST;
      case "classpath" -> ShortenCommandLine.CLASSPATH_FILE;
      default -> null;
    };
  }

  private static boolean noShortenerConfigured(ConfigurationWithCommandLineShortener configuration) {
    return configuration.getShortenCommandLine() == null || 
           configuration.getShortenCommandLine() == ShortenCommandLine.NONE;
  }

  /** @deprecated Use {@link #executeConfiguration(RunnerAndConfigurationSettings, Executor)} */
  @Deprecated
  public static void executeConfiguration(@SuppressWarnings("unused") @NotNull Project project,
                                          @NotNull RunnerAndConfigurationSettings configuration,
                                          @NotNull Executor executor) {
    executeConfiguration(configuration, executor);
  }

  public static void executeConfiguration(@NotNull RunnerAndConfigurationSettings configuration, @NotNull Executor executor) {
    ExecutionEnvironmentBuilder builder;
    try {
      builder = ExecutionEnvironmentBuilder.create(executor, configuration);
    }
    catch (ExecutionException e) {
      LOG.error(e);
      return;
    }

    executeConfiguration(builder.contentToReuse(null).dataContext(null).activeTarget().build(), true, true);
  }

  @NotNull
  public static Icon getConfigurationIcon(@NotNull RunnerAndConfigurationSettings settings, boolean invalid) {
    Icon icon = getRawIcon(settings);
    Icon configurationIcon = settings.isTemporary() ? getTemporaryIcon(icon) : icon;
    if (invalid) {
      return new InvalidRunConfigurationIcon(configurationIcon);
    }
    return configurationIcon;
  }

  @NotNull
  public static Icon getRawIcon(@NotNull RunnerAndConfigurationSettings settings) {
    Icon icon = settings.getFactory().getIcon(settings.getConfiguration());
    return icon == null ? AllIcons.Actions.Help : icon;
  }

  @NotNull
  public static Icon getTemporaryIcon(@NotNull Icon rawIcon) {
    return IconLoader.getTransparentIcon(rawIcon, 0.3f);
  }

  public static @NotNull @NlsSafe String shortenName(@Nullable @NlsSafe String name, int toBeAdded) {
    if (name == null) {
      return "";
    }

    final int symbols = Math.max(10, 20 - toBeAdded);
    return name.length() < symbols + 3 ? name : name.substring(0, symbols) + "...";
  }
}