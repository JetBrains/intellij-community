// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.ide.trustedProjects.TrustedProjectsStateStorage
import com.intellij.openapi.components.*
import com.intellij.openapi.util.io.NioPathPrefixTreeFactory
import com.intellij.util.ThreeState
import com.intellij.util.containers.prefix.map.PrefixTreeMap
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
@Suppress("unused") // Used externally
fun isPathTrustedInSettings(path: Path): Boolean = service<TrustedPathsSettings>().isPathTrusted(path)

@ApiStatus.Internal
@State(name = "Trusted.Paths.Settings", storages = [Storage(value = "trusted-paths.xml", roamingType = RoamingType.DISABLED)])
@Service(Service.Level.APP)
class TrustedPathsSettings : TrustedProjectsStateStorage<TrustedPathsSettings.State>(State()) {

  companion object {
    @JvmStatic
    fun getInstance(): TrustedPathsSettings = service()
  }

  data class State(
    @JvmField
    @field:OptionTag("TRUSTED_PATHS")
    val trustedPaths: List<String> = emptyList()
  ) : TrustedProjectsStateStorage.State {

    @delegate:Transient
    override val trustedState: PrefixTreeMap<Path, Boolean> by lazy {
      NioPathPrefixTreeFactory.createMap(
        trustedPaths.map { Path.of(it) to true }
      )
    }
  }

  fun isPathTrusted(path: Path): Boolean {
    return getProjectPathTrustedState(path) == ThreeState.YES
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

  override fun getProjectTrustedState(locatedProject: TrustedProjectsLocator.LocatedProject): ThreeState {
    val projectTrustedState = super.getProjectTrustedState(locatedProject)
    if (projectTrustedState == ThreeState.YES) {
      TrustedProjectsStatistics.PROJECT_IMPLICITLY_TRUSTED_BY_PATH.log(locatedProject.project)
    }
    return projectTrustedState
  }
}