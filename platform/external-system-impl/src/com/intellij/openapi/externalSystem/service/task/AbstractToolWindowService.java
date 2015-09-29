/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.task;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ExternalEntityData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 5/15/13 1:32 PM
 */
public abstract class AbstractToolWindowService<T extends ExternalEntityData> extends AbstractProjectDataService<T, Void> {

  @Override
  public void importData(@NotNull final Collection<DataNode<T>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (toImport.isEmpty()) {
      return;
    }
    processData(toImport, project);
  }

  protected abstract void processData(@NotNull Collection<DataNode<T>> nodes, @NotNull Project project);
}
