// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal interface RepositoryLibrarySettings {
  fun isSha256ChecksumFeatureEnabled(): Boolean

  fun isJarRepositoryBindingFeatureEnabled(): Boolean

  companion object {
    @JvmStatic
    fun getInstanceOrDefaults(project: Project?): RepositoryLibrarySettings {
      if (project == null) {
        return Defaults
      }
      return Service.getInstance(project)
    }
  }

  private object Defaults : RepositoryLibrarySettings {
    override fun isSha256ChecksumFeatureEnabled() = false

    override fun isJarRepositoryBindingFeatureEnabled() = false
  }

  private class Service : SimplePersistentStateComponent<Service.State>(State()), RepositoryLibrarySettings {
    class State : BaseState() {
      var enableSha256ChecksumFeature by property(Defaults.isSha256ChecksumFeatureEnabled())
      var enableJarRepositoryBindingFeature by property(Defaults.isJarRepositoryBindingFeatureEnabled())
    }

    override fun isSha256ChecksumFeatureEnabled() = state.enableSha256ChecksumFeature

    override fun isJarRepositoryBindingFeatureEnabled() = state.enableJarRepositoryBindingFeature

    companion object {
      fun getInstance(project: Project): RepositoryLibrarySettings = project.service<Service>()
    }
  }
}
