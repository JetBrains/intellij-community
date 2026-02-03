// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.importing;

import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.AbstractNamedData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.util.Couple;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public class ExternalProjectStructureCustomizerImpl extends ExternalProjectStructureCustomizer {

  @Override
  public @NotNull Set<? extends Key<?>> getIgnorableDataKeys() {
    return getDataKeys();
  }

  @Override
  public @NotNull Set<? extends Key<?>> getPublicDataKeys() {
    return getDataKeys();
  }

  @Override
  public @Nullable Icon suggestIcon(@NotNull DataNode node, @NotNull ExternalSystemUiAware uiAware) {
    if(ProjectKeys.PROJECT.equals(node.getKey())) {
      return uiAware.getProjectIcon();
    } else if(ProjectKeys.MODULE.equals(node.getKey())) {
      return uiAware.getProjectIcon();
    }
    return null;
  }

  @Override
  public @NotNull Couple<@Nls String> getRepresentationName(@NotNull DataNode node) {
    if(ProjectKeys.PROJECT.equals(node.getKey())) {
      ProjectData projectData = (ProjectData)node.getData();
      String text = ExternalSystemBundle.message("external.project.structure.project") + " " + projectData.getExternalName();
      return Couple.of(text, projectData.getDescription());
    } else if(ProjectKeys.MODULE.equals(node.getKey())) {
      ModuleData moduleData = (ModuleData)node.getData();
      return Couple.of(moduleData.getId(), moduleData.getDescription());
    }
    return super.getRepresentationName(node);
  }

  private static @NotNull Set<? extends Key<? extends AbstractNamedData>> getDataKeys() {
    return Set.of(ProjectKeys.PROJECT, ProjectKeys.MODULE);
  }
}
