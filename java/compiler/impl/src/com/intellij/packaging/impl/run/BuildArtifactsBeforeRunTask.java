// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.run;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

public class BuildArtifactsBeforeRunTask extends BuildArtifactsBeforeRunTaskBase<BuildArtifactsBeforeRunTask> {
  public static final @NonNls String ARTIFACT_ELEMENT = "artifact";

  public BuildArtifactsBeforeRunTask(Project project) {
    super(BuildArtifactsBeforeRunTaskProvider.ID, project, ARTIFACT_ELEMENT);
  }
}
