/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.packaging.impl.run;

import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.ConfigurationSettingsEditorWrapper;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.packaging.artifacts.*;
import com.intellij.task.*;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class BuildArtifactsBeforeRunTaskProviderBase<T extends BuildArtifactsBeforeRunTaskBase>
  extends BeforeRunTaskProvider<T> {
  private final Project myProject;
  @NotNull final private Class<T> myTaskClass;

  public BuildArtifactsBeforeRunTaskProviderBase(@NotNull Class<T> taskClass, Project project) {
    myProject = project;
    myTaskClass = taskClass;
    project.getMessageBus().connect().subscribe(ArtifactManager.TOPIC, new ArtifactAdapter() {
      @Override
      public void artifactRemoved(@NotNull Artifact artifact) {
        final RunManagerEx runManager = RunManagerEx.getInstanceEx(myProject);
        for (RunConfiguration configuration : runManager.getAllConfigurationsList()) {
          final List<T> tasks = runManager.getBeforeRunTasks(configuration, getId());
          for (T task : tasks) {
            final String artifactName = artifact.getName();
            final List<ArtifactPointer> pointersList = task.getArtifactPointers();
            final ArtifactPointer[] pointers = pointersList.toArray(new ArtifactPointer[pointersList.size()]);
            for (ArtifactPointer pointer : pointers) {
              if (pointer.getArtifactName().equals(artifactName) &&
                  ArtifactManager.getInstance(myProject).findArtifact(artifactName) == null) {
                task.removeArtifact(pointer);
              }
            }
          }
        }
      }
    });
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  public boolean configureTask(@NotNull RunConfiguration runConfiguration, @NotNull T task) {
    final Artifact[] artifacts = ArtifactManager.getInstance(myProject).getArtifacts();
    Set<ArtifactPointer> pointers = new THashSet<>();
    for (Artifact artifact : artifacts) {
      pointers.add(ArtifactPointerManager.getInstance(myProject).createPointer(artifact));
    }
    pointers.addAll(task.getArtifactPointers());
    ArtifactChooser chooser = new ArtifactChooser(new ArrayList<>(pointers));
    chooser.markElements(task.getArtifactPointers());
    chooser.setPreferredSize(JBUI.size(400, 300));

    DialogBuilder builder = new DialogBuilder(myProject);
    builder.setTitle(CompilerBundle.message("build.artifacts.before.run.selector.title"));
    builder.setDimensionServiceKey("#BuildArtifactsBeforeRunChooser");
    builder.addOkAction();
    builder.addCancelAction();
    builder.setCenterPanel(chooser);
    builder.setPreferredFocusComponent(chooser);
    if (builder.show() == DialogWrapper.OK_EXIT_CODE) {
      task.setArtifactPointers(chooser.getMarkedElements());
      return true;
    }
    return false;
  }

  public T createTask(@NotNull RunConfiguration runConfiguration) {
    if (myProject.isDefault()) return null;
    return doCreateTask(myProject);
  }

  @Override
  public boolean canExecuteTask(@NotNull RunConfiguration configuration, @NotNull T task) {
    for (ArtifactPointer pointer : (List<ArtifactPointer>)task.getArtifactPointers()) {
      if (pointer.getArtifact() != null) {
        return true;
      }
    }
    return false;
  }

  public boolean executeTask(DataContext context,
                             @NotNull RunConfiguration configuration,
                             @NotNull final ExecutionEnvironment env,
                             @NotNull final T task) {
    final Ref<Boolean> result = Ref.create(false);
    final Semaphore finished = new Semaphore();

    final List<Artifact> artifacts = new ArrayList<>();
    new ReadAction() {
      protected void run(@NotNull final Result result) {
        List<ArtifactPointer> pointers = task.getArtifactPointers();
        for (ArtifactPointer pointer : pointers) {
          ContainerUtil.addIfNotNull(artifacts, pointer.getArtifact());
        }
      }
    }.execute();

    final ProjectTaskNotification callback = new ProjectTaskNotification() {
      @Override
      public void finished(@NotNull ProjectTaskResult executionResult) {
        result.set(!executionResult.isAborted() && executionResult.getErrors() == 0);
        finished.up();
      }
    };

    ApplicationManager.getApplication().invokeAndWait(() -> {
      if (myProject.isDisposed()) {
        return;
      }
      ProjectTask artifactsBuildProjectTask = createProjectTask(myProject, artifacts);
      finished.down();
      Object sessionId = ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY.get(env);
      ProjectTaskManager.getInstance(myProject).run(new ProjectTaskContext(sessionId), artifactsBuildProjectTask, callback);
    }, ModalityState.NON_MODAL);

    finished.waitFor();
    return result.get();
  }

  protected void setBuildArtifactBeforeRunOption(@NotNull JComponent runConfigurationEditorComponent,
                                                 @NotNull Artifact artifact,
                                                 final boolean enable) {
    final DataContext dataContext = DataManager.getInstance().getDataContext(runConfigurationEditorComponent);
    final ConfigurationSettingsEditorWrapper editor = ConfigurationSettingsEditorWrapper.CONFIGURATION_EDITOR_KEY.getData(dataContext);
    if (editor != null) {
      List<T> tasks = ContainerUtil.findAll(editor.getStepsBeforeLaunch(), myTaskClass);
      if (enable && tasks.isEmpty()) {
        T task = doCreateTask(myProject);
        task.addArtifact(artifact);
        task.setEnabled(true);
        editor.addBeforeLaunchStep(task);
      }
      else {
        for (T task : tasks) {
          if (enable) {
            task.addArtifact(artifact);
            task.setEnabled(true);
          }
          else {
            task.removeArtifact(artifact);
            if (task.getArtifactPointers().isEmpty()) {
              task.setEnabled(false);
            }
          }
        }
      }
    }
  }

  protected abstract T doCreateTask(Project project);

  protected abstract ProjectTask createProjectTask(Project project, List<Artifact> artifacts);
}
