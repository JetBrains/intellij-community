/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.facet.impl.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.facet.FacetType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class FacetEditorsStateManager {

  public static FacetEditorsStateManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FacetEditorsStateManager.class);
  }

  public abstract <T> void saveState(@NotNull FacetType<?, ?> type, @Nullable T state);

  @Nullable
  public abstract <T> T getState(@NotNull FacetType<?, ?> type, @NotNull Class<T> aClass);
}
