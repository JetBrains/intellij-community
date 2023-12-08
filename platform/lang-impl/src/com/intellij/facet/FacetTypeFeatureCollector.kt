// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet

import com.intellij.ide.plugins.PluginFeatureService
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

private class FacetTypeFeatureCollector : ProjectActivity {
  override suspend fun execute(project: Project) {
    serviceAsync<PluginFeatureService>().collectFeatureMapping(
      FacetManagerBase.FEATURE_TYPE,
      FacetType.EP_NAME,
      FacetType<*, *>::getStringId,
      FacetType<*, *>::getPresentableName,
    )
  }
}