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

import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.DataHolder;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Defines common contract for a strategy which is able to manage project data defines in terms of 'external systems' sub-system.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 4/12/13 3:59 PM
 * @param <T>  target project data type
 */
public interface ProjectDataManager<T> {

  ExtensionPointName<ProjectDataManager<?>> EP_NAME = ExtensionPointName.create("EXTERNAL_SYSTEM_PROJECT_DATA_MANAGER");

  /**
   * @return key of project data supported by the current manager
   */
  @NotNull
  Key<T> getTargetDataKey();

  void importData(@NotNull Collection<DataHolder<T>> datas, @NotNull Project project, boolean synchronous);

  void removeData(@NotNull Collection<DataHolder<T>> datas, @NotNull Project project, boolean synchronous);
}
