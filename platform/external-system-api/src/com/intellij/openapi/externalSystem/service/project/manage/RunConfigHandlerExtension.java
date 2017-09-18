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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@ApiStatus.Experimental
public interface RunConfigHandlerExtension {
  ExtensionPointName<RunConfigHandlerExtension> EP_NAME = ExtensionPointName.create("com.intellij.runConfigurationHandlerExtension");

  default void process(@NotNull Project project, @NotNull String name, @NotNull Map<String, Object> cfg) {}
  default void process(@NotNull Module module, @NotNull  String name, @NotNull Map<String, Object> cfg) {}
  default boolean canHandle(@NotNull String typeName) {
    return false;
  }
}