/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.execution.cmd.CommandLineCompletionProvider;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TextAccessor;
import com.intellij.util.BooleanFunction;
import com.intellij.util.containers.ContainerUtil;
import groovyjarjarcommonscli.Options;
import icons.ExternalSystemIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/26/2014
 */
public class TaskCompletionProvider extends CommandLineCompletionProvider {

  private volatile List<LookupElement> myCachedElements;
  private volatile String myCachedWorkingDir;
  private final Project myProject;
  private final ProjectSystemId mySystemId;
  private final TextAccessor myProjectPathAccessor;


  public TaskCompletionProvider(@NotNull Project project,
                                @NotNull ProjectSystemId externalSystemId,
                                @NotNull TextAccessor workDirectoryField) {
    this(project, externalSystemId, workDirectoryField, new Options());
  }

  public TaskCompletionProvider(@NotNull Project project,
                                @NotNull ProjectSystemId externalSystemId,
                                @NotNull TextAccessor workDirectoryField,
                                @NotNull Options options) {
    super(options);
    myProject = project;
    mySystemId = externalSystemId;
    myProjectPathAccessor = workDirectoryField;
  }

  @Override
  protected void addArgumentVariants(@NotNull CompletionResultSet result) {
    List<LookupElement> cachedElements = myCachedElements;
    final String projectPath = myProjectPathAccessor.getText();
    if (cachedElements == null || !StringUtil.equals(myCachedWorkingDir, projectPath)) {
      final ExternalProjectSettings linkedProjectSettings =
        ExternalSystemApiUtil.getSettings(myProject, mySystemId).getLinkedProjectSettings(projectPath);
      if (linkedProjectSettings == null) return;

      final ExternalProjectInfo projectData =
        ProjectDataManager.getInstance().getExternalProjectData(myProject, mySystemId, linkedProjectSettings.getExternalProjectPath());

      if (projectData == null || projectData.getExternalProjectStructure() == null) return;

      final DataNode<?> node =
        ExternalSystemApiUtil.findFirstRecursively(projectData.getExternalProjectStructure(), new BooleanFunction<DataNode<?>>() {
          @Override
          public boolean fun(DataNode<?> node) {
            return node.getKey().equals(ProjectKeys.MODULE) &&
                   node.getData() instanceof ModuleData &&
                   ((ModuleData)node.getData()).getLinkedExternalProjectPath().equals(projectPath);
          }
        });

      final Collection<DataNode<TaskData>> tasks = ExternalSystemApiUtil.getChildren(node, ProjectKeys.TASK);
      cachedElements = ContainerUtil.newArrayListWithCapacity(tasks.size());
      for (DataNode<TaskData> taskDataNode : tasks) {
        cachedElements.add(LookupElementBuilder.create(taskDataNode.getData().getName()).withIcon(ExternalSystemIcons.Task));
      }
      myCachedElements = cachedElements;
      myCachedWorkingDir = projectPath;
    }
    result.addAllElements(cachedElements);
  }
}

