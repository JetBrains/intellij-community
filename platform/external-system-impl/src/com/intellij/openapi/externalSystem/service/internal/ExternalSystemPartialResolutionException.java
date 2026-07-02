// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.internal;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

/**
 * Marker exception to signal that the resolve failed but still produced result.
 *
 * @see ExternalSystemResolveProjectTask#doExecute
 */
@Internal
public class ExternalSystemPartialResolutionException extends ExternalSystemException {

  private final @NotNull DataNode<ProjectData> myPartialProjectData;

  public ExternalSystemPartialResolutionException(@NotNull DataNode<ProjectData> partialProjectData) {
    myPartialProjectData = partialProjectData;
  }

  public @NotNull DataNode<ProjectData> getPartialProjectData() {
    return myPartialProjectData;
  }
}
