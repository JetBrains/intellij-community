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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemProjectKeys;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 4/14/13 11:04 PM
 */
public class ModuleDataManager implements ProjectDataManager<ModuleData> {

  @NotNull
  @Override
  public Key<ModuleData> getTargetDataKey() {
    return ExternalSystemProjectKeys.MODULE;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<ModuleData>> toImport, @NotNull Project project, boolean synchronous) {
    // TODO den implement 
  }

  @Override
  public void removeData(@NotNull Collection<DataNode<ModuleData>> toRemove, @NotNull Project project, boolean synchronous) {
    // TODO den implement 
  }
}
