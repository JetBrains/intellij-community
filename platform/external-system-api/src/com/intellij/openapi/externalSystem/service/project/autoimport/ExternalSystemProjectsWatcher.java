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
package com.intellij.openapi.externalSystem.service.project.autoimport;

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


/**
 * @see ExternalSystemProjectTracker#markDirty
 * @see ExternalSystemProjectTracker#markDirtyAllProjects
 * @see ExternalSystemProjectTracker#scheduleChangeProcessing
 * @deprecated use {@link ExternalSystemProjectTracker} instead
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public interface ExternalSystemProjectsWatcher {
  void markDirtyAllExternalProjects();

  void markDirty(@NotNull Module module);

  void markDirty(@NotNull String projectPath);
}
