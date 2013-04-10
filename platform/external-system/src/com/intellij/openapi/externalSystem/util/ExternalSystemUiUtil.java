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
package com.intellij.openapi.externalSystem.util;

import com.intellij.openapi.externalSystem.model.project.id.GradleSyntheticId;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNodeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 4/8/13 7:29 PM
 */
public class ExternalSystemUiUtil {

  public static final ProjectStructureNodeDescriptor<GradleSyntheticId> DEPENDENCIES_NODE_DESCRIPTOR
    = buildSyntheticDescriptor(ExternalSystemBundle.message("entity.type.dependencies"));

  public static final ProjectStructureNodeDescriptor<GradleSyntheticId> MODULES_NODE_DESCRIPTOR
    = buildSyntheticDescriptor(ExternalSystemBundle.message("entity.type.modules"));

  public static final ProjectStructureNodeDescriptor<GradleSyntheticId> LIBRARIES_NODE_DESCRIPTOR
    = buildSyntheticDescriptor(ExternalSystemBundle.message("entity.type.libraries"));
  
  private ExternalSystemUiUtil() {
  }

  @NotNull
  public static <T extends ProjectEntityId> ProjectStructureNodeDescriptor<T> buildDescriptor(@NotNull T id, @NotNull String name) {
    return new ProjectStructureNodeDescriptor<T>(id, name, id.getType().getIcon());
  }

  @NotNull
  public static ProjectStructureNodeDescriptor<GradleSyntheticId> buildSyntheticDescriptor(@NotNull String text) {
    return buildSyntheticDescriptor(text, null);
  }

  public static ProjectStructureNodeDescriptor<GradleSyntheticId> buildSyntheticDescriptor(@NotNull String text, @Nullable Icon icon) {
    return new ProjectStructureNodeDescriptor<GradleSyntheticId>(new GradleSyntheticId(text), text, icon);
  }
}
