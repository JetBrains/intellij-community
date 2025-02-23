// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.ide.trustedProjects.TrustedProjectsStateStorage
import com.intellij.openapi.components.*
import com.intellij.openapi.util.io.PathPrefixTree
import com.intellij.util.ThreeState
import com.intellij.util.containers.prefixTree.map.PrefixTreeMap
import com.intellij.util.containers.prefixTree.map.toPrefixTreeMap
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
@Suppress("unused") // Used externally
fun isPathTrustedInSettings(path: Path): Boolean = service<TrustedPathsSettings>().isProjectPathTrusted(path)

@ApiStatus.Internal
@State(name = "Trusted.Paths.Settings",
       category = SettingsCategory.TOOLS,
       exportable = true,
       storages = [Storage(value = "trusted-paths.xml", roamingType = RoamingType.DISABLED)])
class TrustedPathsSettings : TrustedProjectsStateStorage<TrustedPathsSettings.State>(State()) {

  companion object {
    @JvmStatic
    fun getInstance(): TrustedPathsSettings = service()
  }

  data class State(
    @JvmField
    @field:OptionTag("TRUSTED_PATHS")
    val trustedPaths: List<String> = emptyList(),
  ) : TrustedProjectsStateStorage.State {


    /**
     * @see TrustedPaths.State.trustedState
     */
    @delegate:Transient
    override val trustedState: PrefixTreeMap<Path, Boolean> by lazy {
      trustedPaths.map { Path.of(it) to true }
        .toPrefixTreeMap(PathPrefixTree)
    }
  }

  fun isProjectPathTrusted(path: Path): Boolean {
    return getProjectPathTrustedState(path) == ThreeState.YES
  }

  fun isProjectTrusted(locatedProject: TrustedProjectsLocator.LocatedProject): Boolean {
    return getProjectTrustedState(locatedProject) == ThreeState.YES
  }

  fun getTrustedPaths(): List<String> {
    return state.trustedPaths
  }

  fun setTrustedPaths(paths: List<String>) {
    updateState {
      State(paths)
    }
  }

  fun addTrustedPath(path: String) {
    updateState {
      State(it.trustedPaths + path)
    }
  }
}