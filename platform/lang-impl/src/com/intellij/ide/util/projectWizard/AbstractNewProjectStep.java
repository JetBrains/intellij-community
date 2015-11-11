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

import com.intellij.ide.util.projectWizard.actions.ProjectSpecificAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.CustomStepProjectGenerator;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.NewDirectoryProjectAction;
import com.intellij.util.Function;
import com.intellij.util.NullableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class AbstractNewProjectStep extends DefaultActionGroup implements DumbAware {

  protected AbstractNewProjectStep(@NotNull Customization customization) {
    super("Select Project Type", true);

    NullableConsumer<ProjectSettingsStepBase> callback = customization.createCallback();

    final DirectoryProjectGenerator emptyProjectGenerator = customization.createEmptyProjectGenerator();
    ProjectSpecificAction projectSpecificAction =
      new ProjectSpecificAction(emptyProjectGenerator, customization.createProjectSpecificSettingsStep(emptyProjectGenerator, callback));
    addAll(projectSpecificAction.getChildren(null));

    DirectoryProjectGenerator[] generators = customization.getProjectGenerators();
    customization.setUpBasicAction(projectSpecificAction, generators);

    for (DirectoryProjectGenerator generator : generators) {
      addAll(customization.getActions(generator, callback));
    }

    addAll(customization.getExtraActions(callback));
  }

  protected static abstract class Customization {
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
      NewDirectoryProjectAction.doGenerateProject(project, settings.getProjectLocation(), generator,
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
}