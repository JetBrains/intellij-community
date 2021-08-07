// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts

import com.intellij.ide.plugins.PluginFeatureService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.packaging.artifacts.ArtifactType

private class ArtifactTypeFeatureCollector : StartupActivity.Background {
  override fun runActivity(project: Project) {
    PluginFeatureService.instance.collectFeatureMapping(
      ArtifactManagerImpl.FEATURE_TYPE,
      ArtifactType.EP_NAME,
      ArtifactType::getId,
      ArtifactType::getPresentableName,
    )
  }
}
