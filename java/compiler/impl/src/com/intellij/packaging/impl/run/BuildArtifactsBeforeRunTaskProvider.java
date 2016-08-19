/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.ConfigurationSettingsEditorWrapper;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class BuildArtifactsBeforeRunTaskProvider extends BeforeRunTaskProvider<BuildArtifactsBeforeRunTask> {
  @NonNls public static final String BUILD_ARTIFACTS_ID = "BuildArtifacts";
  public static final Key<BuildArtifactsBeforeRunTask> ID = Key.create(BUILD_ARTIFACTS_ID);
  private final Project myProject;

  public BuildArtifactsBeforeRunTaskProvider(Project project) {
    myProject = project;
    project.getMessageBus().connect().subscribe(ArtifactManager.TOPIC, new ArtifactAdapter() {
      @Override
      public void artifactRemoved(@NotNull Artifact artifact) {
        final RunManagerEx runManager = RunManagerEx.getInstanceEx(myProject);
        for (RunConfiguration configuration : runManager.getAllConfigurationsList()) {
          final List<BuildArtifactsBeforeRunTask> tasks = runManager.getBeforeRunTasks(configuration, ID);
          for (BuildArtifactsBeforeRunTask task : tasks) {
            final String artifactName = artifact.getName();
            final List<ArtifactPointer> pointersList = task.getArtifactPointers();
            final ArtifactPointer[] pointers = pointersList.toArray(new ArtifactPointer[pointersList.size()]);
            for (ArtifactPointer pointer : pointers) {
              if (pointer.getArtifactName().equals(artifactName) && ArtifactManager.getInstance(myProject).findArtifact(artifactName) == null) {
                task.removeArtifact(pointer);
              }
            }
          }
        }
      }
    });
  }

  public Key<BuildArtifactsBeforeRunTask> getId() {
    return ID;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Artifact;
  }

  @Override
  public String getName() {
    return CompilerBundle.message("build.artifacts.before.run.description.empty");
  }

  @Override
  public Icon getTaskIcon(BuildArtifactsBeforeRunTask task) {
    List<ArtifactPointer> pointers = task.getArtifactPointers();
    if (pointers == null || pointers.isEmpty())
      return getIcon();
    Artifact artifact = pointers.get(0).getArtifact();
    if (artifact == null)
      return getIcon();
    return artifact.getArtifactType().getIcon();
  }

  @Override
  public String getDescription(BuildArtifactsBeforeRunTask task) {
    final List<ArtifactPointer> pointers = task.getArtifactPointers();
    if (pointers.isEmpty()) {
      return CompilerBundle.message("build.artifacts.before.run.description.empty");
    }
    if (pointers.size() == 1) {
      return CompilerBundle.message("build.artifacts.before.run.description.single", pointers.get(0).getArtifactName());
    }
    return CompilerBundle.message("build.artifacts.before.run.description.multiple", pointers.size());
  }

  public boolean isConfigurable() {
    return true;
  }

  public BuildArtifactsBeforeRunTask createTask(RunConfiguration runConfiguration) {
    if (myProject.isDefault()) return null;
    return new BuildArtifactsBeforeRunTask(myProject);
  }

  public boolean configureTask(RunConfiguration runConfiguration, BuildArtifactsBeforeRunTask task) {
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

  @Override
  public boolean canExecuteTask(RunConfiguration configuration, BuildArtifactsBeforeRunTask task) {
    for (ArtifactPointer pointer:  task.getArtifactPointers()) {
      if (pointer.getArtifact() != null)
        return true;
    }
    return false;
  }

  public boolean executeTask(DataContext context,
                             RunConfiguration configuration,
                             final ExecutionEnvironment env,
                             final BuildArtifactsBeforeRunTask task) {
    final Ref<Boolean> result = Ref.create(false);
    final Semaphore finished = new Semaphore();

    final List<Artifact> artifacts = new ArrayList<>();
    new ReadAction() {
      protected void run(@NotNull final Result result) {
        for (ArtifactPointer pointer : task.getArtifactPointers()) {
          ContainerUtil.addIfNotNull(artifacts, pointer.getArtifact());
        }
      }
    }.execute();
    
    final CompileStatusNotification callback = new CompileStatusNotification() {
      public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        result.set(!aborted && errors == 0);
        finished.up();
      }
    };

    ApplicationManager.getApplication().invokeAndWait(() -> {
      if (myProject.isDisposed()) {
        return;
      }
      final CompilerManager manager = CompilerManager.getInstance(myProject);
      final CompileScope scope = ArtifactCompileScope.createArtifactsScope(myProject, artifacts);
      ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY.set(scope, ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY.get(env));
      finished.down();
      manager.make(scope, CompilerFilter.ALL, callback);
    }, ModalityState.NON_MODAL);

    finished.waitFor();
    return result.get();
  }

  public static void setBuildArtifactBeforeRunOption(@NotNull JComponent runConfigurationEditorComponent,
                                                     Project project,
                                                     @NotNull Artifact artifact,
                                                     final boolean enable) {
    final DataContext dataContext = DataManager.getInstance().getDataContext(runConfigurationEditorComponent);
    final ConfigurationSettingsEditorWrapper editor = ConfigurationSettingsEditorWrapper.CONFIGURATION_EDITOR_KEY.getData(dataContext);
    if (editor != null) {
      List<BeforeRunTask> tasks = editor.getStepsBeforeLaunch();
      List<BuildArtifactsBeforeRunTask> myTasks = new ArrayList<>();
      for (BeforeRunTask task : tasks) {
        if (task instanceof BuildArtifactsBeforeRunTask) {
          myTasks.add((BuildArtifactsBeforeRunTask)task);
        }
      }
      if (enable && myTasks.isEmpty()) {
        BuildArtifactsBeforeRunTask task = new BuildArtifactsBeforeRunTask(project);
        task.addArtifact(artifact);
        task.setEnabled(true);
        editor.addBeforeLaunchStep(task);
      }
      else {
        for (BuildArtifactsBeforeRunTask task : myTasks) {
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

  public static void setBuildArtifactBeforeRun(@NotNull Project project, @NotNull RunConfiguration configuration, @NotNull Artifact artifact) {
    RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
    final List<BuildArtifactsBeforeRunTask> buildArtifactsTasks = runManager.getBeforeRunTasks(configuration, ID);
    if (buildArtifactsTasks.isEmpty()) { //Add new task if absent
      BuildArtifactsBeforeRunTask task = new BuildArtifactsBeforeRunTask(project);
      buildArtifactsTasks.add(task);
      List<BeforeRunTask> tasks = runManager.getBeforeRunTasks(configuration);
      tasks.add(task);
      runManager.setBeforeRunTasks(configuration, tasks, false);
    }

    for (BuildArtifactsBeforeRunTask task : buildArtifactsTasks) {
      task.setEnabled(true);
      task.addArtifact(artifact);

    }
  }
}
