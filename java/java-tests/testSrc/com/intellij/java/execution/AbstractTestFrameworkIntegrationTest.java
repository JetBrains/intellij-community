// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.JavaTestFrameworkRunnableState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.idea.maven.aether.ProgressConsumer;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AbstractTestFrameworkIntegrationTest extends BaseConfigurationTestCase {
  public static ProcessOutput doStartTestsProcess(RunConfiguration configuration) throws ExecutionException {
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    Project project = configuration.getProject();
    RunnerAndConfigurationSettingsImpl
      settings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration, false);
    ExecutionEnvironment
      environment = new ExecutionEnvironment(executor, ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, settings.getConfiguration()), settings, project);
    JavaTestFrameworkRunnableState<?> state = ((JavaTestConfigurationBase)configuration).getState(executor, environment);
    state.appendForkInfo(executor);
    state.appendRepeatMode();

    JavaParameters parameters = state.getJavaParameters();
    parameters.setUseDynamicClasspath(project);
    //parameters.getVMParametersList().addParametersString("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5007");
    GeneralCommandLine commandLine = parameters.toCommandLine();

    OSProcessHandler process = new OSProcessHandler(commandLine);
    final SearchForTestsTask searchForTestsTask = state.createSearchingForTestsTask();
    if (searchForTestsTask != null) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
                                                                          searchForTestsTask.run(new EmptyProgressIndicator());
                                                                          ApplicationManager.getApplication().invokeLater(() -> searchForTestsTask.onSuccess());
                                                                        },
                                                                        "", false, project, null);
    }

    ProcessOutput processOutput = new ProcessOutput();
    process.addProcessListener(new ProcessAdapter() {
      @Override
      public void startNotified(@NotNull ProcessEvent event) {
        if (searchForTestsTask != null) {
          searchForTestsTask.finish();
        }
      }

      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        String text = event.getText();
        if (StringUtil.isEmptyOrSpaces(text)) return;
        try {
          if (outputType == ProcessOutputTypes.STDOUT) {
            ServiceMessage serviceMessage = ServiceMessage.parse(text.trim());
            if (serviceMessage == null) {
              processOutput.out.add(text);
            }
            else {
              processOutput.messages.add(serviceMessage);
            }
          }

          if (outputType == ProcessOutputTypes.SYSTEM) {
            processOutput.sys.add(text);
          }

          if (outputType == ProcessOutputTypes.STDERR) {
            processOutput.err.add(text);
          }
        }
        catch (ParseException e) {
          e.printStackTrace();
          System.err.println(text);
        }
      }
    });
    process.startNotify();
    process.waitFor();
    process.destroyProcess();

    return processOutput;
  }


  protected static void addMavenLibs(Module module,
                                     JpsMavenRepositoryLibraryDescriptor descriptor) throws Exception {
    addMavenLibs(module, descriptor, getRepoManager());
  }

  protected static void addMavenLibs(Module module,
                                     JpsMavenRepositoryLibraryDescriptor descriptor,
                                     ArtifactRepositoryManager repoManager) throws Exception {

    Collection<File> files = repoManager.resolveDependency(descriptor.getGroupId(), descriptor.getArtifactId(), descriptor.getVersion(),
                                                           descriptor.isIncludeTransitiveDependencies(), descriptor.getExcludedDependencies());
    assertFalse("No files retrieved for: " + descriptor.getGroupId(), files.isEmpty());
    for (File artifact : files) {
      VirtualFile libJarLocal = LocalFileSystem.getInstance().findFileByIoFile(artifact);
      assertNotNull(libJarLocal);
      VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(libJarLocal);
      ModuleRootModificationUtil.addModuleLibrary(module, jarRoot.getUrl());
    }
  }

  protected static ArtifactRepositoryManager getRepoManager() {
    final File localRepo = new File(SystemProperties.getUserHome(), ".m2/repository");
    return new ArtifactRepositoryManager(
      localRepo,
      Collections.singletonList(ArtifactRepositoryManager.createRemoteRepository("maven", "https://repo.labs.intellij.net/repo1")),
      new ProgressConsumer() {
        @Override
        public void consume(String message) {
          System.out.println(message);
        }
      }
    );
  }

  public static class ProcessOutput {
    public List<String> out = new ArrayList<>();
    public List<String> err = new ArrayList<>();
    public List<String> sys = new ArrayList<>();
    public List<ServiceMessage> messages = new ArrayList<>();
  }
}
