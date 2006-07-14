/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.lang.ant.config;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public abstract class AntConfiguration implements ProjectComponent {

  private static final Map<Project, AntConfiguration> CONFIGURATIONS = new HashMap<Project, AntConfiguration>();
  private final Project myProject;

  protected AntConfiguration(final Project project) {
    myProject = project;
    CONFIGURATIONS.put(project, this);
  }

  public static AntConfiguration getInstance(final Project project) {
    return CONFIGURATIONS.get(project);
  }

  public Project getProject() {
    return myProject;
  }

  public abstract AntBuildFile[] getBuildFiles();

  public abstract AntBuildFile addBuildFile(final VirtualFile file) throws AntNoFileException;

  public abstract void removeBuildFile(final AntBuildFile file);

  public abstract void addAntConfigurationListener(final AntConfigurationListener listener);

  public abstract void removeAntConfigurationListener(final AntConfigurationListener listener);

  public abstract AntBuildTarget[] getMetaTargets(final AntBuildFile buildFile);

  public abstract void updateBuildFile(final AntBuildFile buildFile);

  @Nullable
  public abstract AntBuildModel getModelIfRegistered(final AntBuildFile buildFile);

  public abstract AntBuildModel getModel(final AntBuildFile buildFile);

  @Nullable
  public abstract AntBuildFile findBuildFileByActionId(final String id);

  public abstract boolean hasTasksToExecuteBeforeRun(final RunConfiguration configuration);

  public abstract boolean executeTaskBeforeRun(final DataContext context, final RunConfiguration configuration);
}