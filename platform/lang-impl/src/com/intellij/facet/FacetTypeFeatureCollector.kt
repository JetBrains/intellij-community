// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet

import com.intellij.ide.plugins.PluginFeatureService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class FacetTypeFeatureCollector : StartupActivity.Background {
  override fun runActivity(project: Project) {
    PluginFeatureService.instance.collectFeatureMapping(
      FacetManagerBase.FEATURE_TYPE,
      FacetType.EP_NAME,
      FacetType<*, *>::getStringId,
      FacetType<*, *>::getPresentableName,
    )
  }
}