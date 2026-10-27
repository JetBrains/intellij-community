// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.Location;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import java.util.function.Consumer;

@TestOnly
public final class ExecutionTestUtil {
  private static final Logger LOG = Logger.getInstance(ExecutionTestUtil.class);

  public static <T extends RunConfiguration> @Nullable T getRunConfiguration(@NotNull PsiElement element, @NotNull RunConfigurationProducer<T> producer) {
    var dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, element.getProject())
      .add(PlatformCoreDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(element))
      .add(Location.DATA_KEY, PsiLocation.fromPsiElement(element))
      .build();

    var cc = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN);

    var configuration = producer.createConfigurationFromContext(cc);
    //noinspection unchecked
    return configuration != null ? (T)configuration.getConfiguration() : null;
  }

  /**
   * Executes {@code runConfiguration} with {@link DefaultRunExecutor#EXECUTOR_ID run} executor,
   * then waits for 60 seconds till the process ends.
   */
  public static @NotNull ExecutionEnvironment executeConfigurationAndWait(@NotNull RunConfiguration runConfiguration) throws InterruptedException {
    return executeConfigurationAndWait(runConfiguration, DefaultRunExecutor.EXECUTOR_ID);
  }

  /**
   * Executes {@code runConfiguration} with {@link DefaultRunExecutor#EXECUTOR_ID run} executor,
   * then waits for {@code timeoutInSeconds} seconds till the process ends.
   */
  public static @NotNull ExecutionEnvironment executeConfigurationAndWait(
    @NotNull RunConfiguration runConfiguration,
    long timeoutInSeconds
  ) throws InterruptedException {
    return executeConfigurationAndWait(runConfiguration, DefaultRunExecutor.EXECUTOR_ID, timeoutInSeconds);
  }

  /**
   * Executes {@code runConfiguration} with executor {@code executorId}, then waits for 60 seconds till the process ends.
   */
  public static @NotNull ExecutionEnvironment executeConfigurationAndWait(
    @NotNull RunConfiguration runConfiguration,
    @NotNull String executorId
  ) throws InterruptedException {
    return executeConfigurationAndWait(runConfiguration, executorId, 60);
  }

  /**
   * Executes {@code runConfiguration} with executor {@code executorId},
   * then waits for the {@code timeoutInSeconds} seconds till the process ends.
   */
  public static @NotNull ExecutionEnvironment executeConfigurationAndWait(
    @NotNull RunConfiguration runConfiguration,
    @NotNull String executorId,
    long timeoutInSeconds
  ) throws InterruptedException {
    var result = executeConfiguration(runConfiguration, executorId, null);
    var processHandler = result.second.getProcessHandler();
    Assert.assertNotNull("Process handler must not be null!", processHandler);
    PlatformTestUtil.waitWithEventsDispatching(
      () -> "Process failed to finish in " + timeoutInSeconds + " seconds: " + processHandler,
      () -> processHandler.isProcessTerminated() && processHandler.getExitCode() != null, Math.toIntExact(timeoutInSeconds),
      () -> {
        if (!processHandler.isProcessTerminated()) {
          LOG.debug("Destroying process: " + processHandler);
          processHandler.destroyProcess();
        }
      });

    return result.first;
  }

  /**
   * @see PlatformTestUtil#executeConfiguration(RunConfiguration, Executor, Consumer)
   */
  public static @NotNull Pair<@NotNull ExecutionEnvironment, RunContentDescriptor> executeConfiguration(
    @NotNull RunConfiguration runConfiguration,
    @NotNull String executorId,
    @Nullable Consumer<? super RunContentDescriptor> contentDescriptorProcessor
  ) throws InterruptedException {
    var executor = ExecutorRegistry.getInstance().getExecutorById(executorId);
    Assert.assertNotNull("Unable to find executor: " + executorId, executor);
    return executeConfiguration(runConfiguration, executor, contentDescriptorProcessor);
  }

  /**
   * Executes {@code runConfiguration} with executor defined by {@code executorId} and returns a pair of {@link ExecutionEnvironment} and
   * {@link RunContentDescriptor}.
   *
   * @param descriptorProcessor optional processor for the run content descriptor of executed configuration
   */
  public static @NotNull Pair<@NotNull ExecutionEnvironment, RunContentDescriptor> executeConfiguration(
    @NotNull RunConfiguration runConfiguration,
    @NotNull Executor executor,
    @Nullable Consumer<? super RunContentDescriptor> descriptorProcessor
  ) throws InterruptedException {
    var project = runConfiguration.getProject();
    var factory = runConfiguration.getFactory();
    if (factory == null) {
      Assert.fail("No factory found for: " + runConfiguration);
    }
    var runnerAndConfigurationSettings = RunManager.getInstance(project).createConfiguration(runConfiguration, factory);
    var runner = ProgramRunner.getRunner(executor.getId(), runConfiguration);
    if (runner == null) {
      Assert.fail("No runner found for: " + executor.getId() + " and " + runConfiguration);
    }
    var refRunContentDescriptor = new Ref<RunContentDescriptor>();
    var executionEnvironment = new ExecutionEnvironment(executor, runner, runnerAndConfigurationSettings, project);
    var failure = new boolean[]{false};
    ProgramRunnerUtil.executeConfigurationAsync(executionEnvironment, false, false, new ProgramRunner.Callback() {
      @Override
      public void processNotStarted(@Nullable Throwable error) {
        failure[0] = true;
      }

      @Override
      public void processStarted(RunContentDescriptor descriptor) {
        var processHandler = descriptor.getProcessHandler();
        LOG.debug("Process started: ", processHandler);
        if (descriptorProcessor != null) {
          descriptorProcessor.accept(descriptor);
        }
        Assert.assertNotNull(processHandler);
        processHandler.addProcessListener(new ProcessListener() {
          @Override
          public void startNotified(@NotNull ProcessEvent event) {
            LOG.debug("Process notified: ", processHandler);
          }

          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            LOG.debug("Process terminated: exitCode: ", event.getExitCode(), "; text: ", event.getText(), "; process: ", processHandler);
          }

          @Override
          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            LOG.debug(outputType + ": " + event.getText());
          }
        });
        refRunContentDescriptor.set(descriptor);
      }
    });
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    PlatformTestUtil.waitWithEventsDispatching("Process failed to start in 60 seconds", () -> !refRunContentDescriptor.isNull() || failure[0], 60);
    Assert.assertFalse("Process could not start for configuration: " + runConfiguration, failure[0]);
    return Pair.create(executionEnvironment, refRunContentDescriptor.get());
  }
}
