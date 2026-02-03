// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.viewModel.extraction;

import com.intellij.openapi.client.ClientProjectSession;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

/**
 * This interface is used in Code With Me and Remove Development to determine
 * how a given project view pane is converted for a given client.
 */
@Internal
public interface ProjectViewPaneModelExtractor {
  ExtensionPointName<ProjectViewPaneModelExtractor> EP_NAME = ExtensionPointName.create("com.intellij.projectViewPaneExtractor");

  boolean isApplicable(@NotNull String paneId, @NotNull ClientProjectSession session);

  @NotNull
  ProjectViewPaneExtractorMode getMode();
}