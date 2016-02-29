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
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.util.projectWizard.actions.ProjectSpecificAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.CustomStepProjectGenerator;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectOpenedCallback;
import com.intellij.util.Function;
import com.intellij.util.NullableConsumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;


public class AbstractNewProjectStep extends DefaultActionGroup implements DumbAware {
  private static final Logger LOG = Logger.getInstance(AbstractNewProjectStep.class);

  protected AbstractNewProjectStep(@NotNull Customization customization) {
    super("Select Project Type", true);

    NullableConsumer<ProjectSettingsStepBase> callback = customization.createCallback();
    ProjectSpecificAction projectSpecificAction = customization.createProjectSpecificAction(callback);
    addProjectSpecificAction(projectSpecificAction);

    DirectoryProjectGenerator[] generators = customization.getProjectGenerators();
    customization.setUpBasicAction(projectSpecificAction, generators);

    addAll(customization.getActions(generators, callback));
    addAll(customization.getExtraActions(callback));
  }

  protected void addProjectSpecificAction(@NotNull final ProjectSpecificAction projectSpecificAction) {
    addAll(projectSpecificAction.getChildren(null));
  }

  protected static abstract class Customization {
    @NotNull
    protected ProjectSpecificAction createProjectSpecificAction(@NotNull final NullableConsumer<ProjectSettingsStepBase> callback) {
      DirectoryProjectGenerator emptyProjectGenerator = createEmptyProjectGenerator();
      return new ProjectSpecificAction(emptyProjectGenerator, createProjectSpecificSettingsStep(emptyProjectGenerator, callback));
    }

    @NotNull
    protected abstract NullableConsumer<ProjectSettingsStepBase> createCallback();

    @NotNull
    protected abstract DirectoryProjectGenerator createEmptyProjectGenerator();

    @NotNull
    protected abstract ProjectSettingsStepBase createProjectSpecificSettingsStep(@NotNull DirectoryProjectGenerator emptyProjectGenerator,
                                                                                 @NotNull NullableConsumer<ProjectSettingsStepBase> callback);


    @NotNull
    protected DirectoryProjectGenerator[] getProjectGenerators() {
      return Extensions.getExtensions(DirectoryProjectGenerator.EP_NAME);
    }

    public AnAction[] getActions(@NotNull DirectoryProjectGenerator[] generators, @NotNull NullableConsumer<ProjectSettingsStepBase> callback) {
      final List<AnAction> actions = ContainerUtil.newArrayList();
      for (DirectoryProjectGenerator projectGenerator : generators) {
        actions.addAll(ContainerUtil.list(getActions(projectGenerator, callback)));
      }
      return actions.toArray(new AnAction[actions.size()]);
    }

    @NotNull
    public AnAction[] getActions(@NotNull DirectoryProjectGenerator generator, @NotNull NullableConsumer<ProjectSettingsStepBase> callback) {
      if (shouldIgnore(generator)) {
        return AnAction.EMPTY_ARRAY;
      }

      ProjectSettingsStepBase step = generator instanceof CustomStepProjectGenerator ?
                                     ((ProjectSettingsStepBase)((CustomStepProjectGenerator)generator).createStep(generator, callback)) :
                                     createProjectSpecificSettingsStep(generator, callback);

      ProjectSpecificAction projectSpecificAction = new ProjectSpecificAction(generator, step);
      return projectSpecificAction.getChildren(null);
    }

    protected boolean shouldIgnore(@NotNull DirectoryProjectGenerator generator) {
      return false;
    }

    @NotNull
    public AnAction[] getExtraActions(@NotNull NullableConsumer<ProjectSettingsStepBase> callback) {
      return AnAction.EMPTY_ARRAY;
    }

    public void setUpBasicAction(@NotNull ProjectSpecificAction projectSpecificAction, @NotNull DirectoryProjectGenerator[] generators) {
    }
  }

  protected static abstract class AbstractCallback implements NullableConsumer<ProjectSettingsStepBase> {
    @Override
    public void consume(@Nullable final ProjectSettingsStepBase settings) {
      if (settings == null) return;

      final Project project = ProjectManager.getInstance().getDefaultProject();
      final DirectoryProjectGenerator generator = settings.getProjectGenerator();
      doGenerateProject(project, settings.getProjectLocation(), generator,
                                                  new Function<VirtualFile, Object>() {
                                                    @Override
                                                    public Object fun(VirtualFile file) {
                                                      return getProjectSettings(generator);
                                                    }
                                                  });
    }

    @Nullable
    abstract protected Object getProjectSettings(@NotNull DirectoryProjectGenerator generator);
  }

  public static Project doGenerateProject(@Nullable final Project project,
                                          @NotNull final String locationString,
                                          @Nullable final DirectoryProjectGenerator generator,
                                          @NotNull final Function<VirtualFile, Object> settingsComputable) {
    final File location = new File(FileUtil.toSystemDependentName(locationString));
    if (!location.exists() && !location.mkdirs()) {
      String message = ActionsBundle.message("action.NewDirectoryProject.cannot.create.dir", location.getAbsolutePath());
      Messages.showErrorDialog(project, message, ActionsBundle.message("action.NewDirectoryProject.title"));
      return null;
    }

    final VirtualFile baseDir = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(location);
      }
    });
    if (baseDir == null) {
      LOG.error("Couldn't find '" + location + "' in VFS");
      return null;
    }
    baseDir.refresh(false, true);

    if (baseDir.getChildren().length > 0) {
      String message = ActionsBundle.message("action.NewDirectoryProject.not.empty", location.getAbsolutePath());
      int rc = Messages.showYesNoDialog(project, message, ActionsBundle.message("action.NewDirectoryProject.title"), Messages.getQuestionIcon());
      if (rc == Messages.YES) {
        return PlatformProjectOpenProcessor.getInstance().doOpenProject(baseDir, null, false);
      }
    }

    String generatorName = generator == null ? "empty" : ConvertUsagesUtil.ensureProperKey(generator.getName());
    UsageTrigger.trigger("AbstractNewProjectStep." + generatorName);

    Object settings = null;
    if (generator != null) {
      try {
        settings = settingsComputable.fun(baseDir);
      }
      catch (ProcessCanceledException e) {
        return null;
      }
    }
    RecentProjectsManager.getInstance().setLastProjectCreationLocation(location.getParent());
    final Object finalSettings = settings;
    return PlatformProjectOpenProcessor.doOpenProject(baseDir, null, false, -1, new ProjectOpenedCallback() {
      @Override
      public void projectOpened(Project project, Module module) {
        if (generator != null) {
          generator.generateProject(project, baseDir, finalSettings, module);
        }
      }
    }, false);
  }

}