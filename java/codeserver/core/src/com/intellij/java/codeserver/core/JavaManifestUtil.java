// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity;
import com.intellij.java.workspace.entities.JavaModuleSettingsKt;
import com.intellij.openapi.module.Module;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.jps.entities.ModuleId;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods related to manifest declarations
 */
public final class JavaManifestUtil {
  /**
   * @param module module to find manifest in
   * @param attribute attribute name from a manifest file
   * @return attribute value, null if not found
   */
  @Contract(pure = true)
  public static @Nullable String getManifestAttributeValue(@NotNull Module module, @NotNull String attribute) {
    ModuleEntity entity = WorkspaceModel.getInstance(module.getProject())
      .getCurrentSnapshot()
      .resolve(new ModuleId(module.getName()));
    if (entity == null) return null;
    JavaModuleSettingsEntity settings = JavaModuleSettingsKt.getJavaSettings(entity);
    if (settings == null) return null;
    return settings.getManifestAttributes().get(attribute);
  }
}
