// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal interface RepositoryLibrarySettings {
  fun isSha256ChecksumUiSettingsDisplayed(): Boolean

  fun isSha256ChecksumAutoBuildEnabled(): Boolean

  fun isBindJarRepositoryUiSettingsDisplayed(): Boolean

  fun isJarRepositoryAutoBindEnabled(): Boolean

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
    override fun isSha256ChecksumUiSettingsDisplayed() = false
    override fun isSha256ChecksumAutoBuildEnabled() = false
    override fun isBindJarRepositoryUiSettingsDisplayed() = false
    override fun isJarRepositoryAutoBindEnabled() = false
  }

  private class Service : SimplePersistentStateComponent<Service.State>(State()), RepositoryLibrarySettings {
    class State : BaseState() {
      /**
       * Display SHA256 checksum internal actions and SHA256 checksum checkbox in library editor.
       */
      var displaySha256ChecksumUiSettings by property(Defaults.isSha256ChecksumUiSettingsDisplayed())

      /**
       * Build SHA256 checksum when new library is added.
       */
      var sha256ChecksumAutoBuild by property(Defaults.isSha256ChecksumAutoBuildEnabled())

      /**
       * Display Repository binding internal actions and library editor combo box.
       */
      var displayJarRepositoryBindingUi by property(Defaults.isBindJarRepositoryUiSettingsDisplayed())

      /**
       * Guess and bind JAR repository to a new library when one is added. This operation may fail (ex. if library
       * is stored in one repository, but its dependencies in another), so a user may change library settings manually.
       */
      var jarRepositoryAutoBind by property(Defaults.isJarRepositoryAutoBindEnabled())
    }

    override fun isSha256ChecksumUiSettingsDisplayed() = state.displaySha256ChecksumUiSettings

    override fun isSha256ChecksumAutoBuildEnabled() = state.sha256ChecksumAutoBuild

    override fun isBindJarRepositoryUiSettingsDisplayed() = state.displayJarRepositoryBindingUi

    override fun isJarRepositoryAutoBindEnabled() = state.displayJarRepositoryBindingUi && state.jarRepositoryAutoBind

    companion object {
      fun getInstance(project: Project): RepositoryLibrarySettings = project.service<Service>()
    }
  }
}
