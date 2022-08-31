// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.artifacts

import com.intellij.ide.plugins.PluginFeatureService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.artifacts.ArtifactType

private class ArtifactTypeFeatureCollector : ProjectPostStartupActivity {

  override suspend fun execute(project: Project) {
    PluginFeatureService.instance.collectFeatureMapping(
      ArtifactManager.FEATURE_TYPE,
      ArtifactType.EP_NAME,
      ArtifactType::getId,
      ArtifactType::getPresentableName,
    )
  }
}
