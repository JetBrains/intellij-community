// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.elements;

import com.intellij.openapi.project.Project;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.CompositePackagingElementEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.PackagingElementEntity;
import org.jetbrains.annotations.NotNull;

/**
 * This interface was introduced to get an opportunity at {@link CompositePackagingElement} to initialize
 * elements from other modules e.g `intellij.java.compiler.impl`. This case is needed for the new project model
 * when we have not changed root, but child elements changed and thus we need to update/add external mapping.
 * This case was found at {@link com.intellij.java.configurationStore.ReloadProjectTest}
 */
public interface ElementInitializer {
  PackagingElement initialize(@NotNull PackagingElementEntity entity, @NotNull Project project, @NotNull EntityStorage storage);
  PackagingElement initialize(@NotNull CompositePackagingElementEntity entity, @NotNull Project project, @NotNull EntityStorage storage);
}
