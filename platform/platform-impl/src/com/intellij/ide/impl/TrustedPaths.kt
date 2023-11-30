// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.ide.trustedProjects.TrustedProjectsStateStorage
import com.intellij.openapi.components.*
import com.intellij.openapi.util.io.NioPathPrefixTreeFactory
import com.intellij.util.containers.prefix.map.PrefixTreeMap
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
@State(name = "Trusted.Paths", storages = [Storage(value = "trusted-paths.xml", roamingType = RoamingType.DISABLED)])
@Service(Service.Level.APP)
class TrustedPaths : TrustedProjectsStateStorage<TrustedPaths.State>(State()) {

  companion object {
    @JvmStatic
    fun getInstance(): TrustedPaths = service()
  }

  data class State(
    @JvmField
    @field:OptionTag("TRUSTED_PROJECT_PATHS")
    val trustedPaths: Map<String, Boolean> = emptyMap()
  ) : TrustedProjectsStateStorage.State {

    @delegate:Transient
    override val trustedState: PrefixTreeMap<Path, Boolean> by lazy {
      NioPathPrefixTreeFactory.createMap(
        trustedPaths.entries.map { Path.of(it.key) to it.value }
      )
    }
  }

  fun setProjectPathTrusted(path: Path, value: Boolean) {
    updateState {
      State(it.trustedPaths + (path.toString() to value))
    }
  }

  fun setProjectTrustedState(locatedProject: LocatedProject, isTrusted: Boolean) {
    val additionalTrustedState = locatedProject.projectRoots
      .associate { it.toString() to isTrusted }
    updateState {
      State(it.trustedPaths + additionalTrustedState)
    }
  }
}