// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PluginFeatureEnabler {

  fun enableSuggested()

  companion object {

    @JvmStatic
    fun getInstanceIfCreated(project: Project): PluginFeatureEnabler? =
      project.getServiceIfCreated(PluginFeatureEnabler::class.java)
  }
}