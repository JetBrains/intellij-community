// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.openapi.components.*
import com.intellij.util.io.isAncestor
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.*

@ApiStatus.Internal
fun isPathTrustedInSettings(path: Path): Boolean = service<TrustedPathsSettings>().isPathTrusted(path)

@ApiStatus.Internal
@State(name = "Trusted.Paths.Settings", storages = [Storage(value = "trusted-paths.xml", roamingType = RoamingType.DISABLED)])
@Service(Service.Level.APP)
class TrustedPathsSettings : SimplePersistentStateComponent<TrustedPathsSettings.State>(State()) {

  companion object {
    @JvmStatic
    fun getInstance(): TrustedPathsSettings = service()
  }

  class State : BaseState() {
    @get:OptionTag("TRUSTED_PATHS")
    var trustedPaths by list<String>()
  }

  fun isPathTrusted(path: Path): Boolean {
    return state.trustedPaths.asSequence()
      .mapNotNull { path.fileSystem.getPath(it) }
      .any { it.isAncestor(path) }
  }

  fun getTrustedPaths(): List<String> = Collections.unmodifiableList(state.trustedPaths)

  fun setTrustedPaths(paths: List<String>) {
    state.trustedPaths = paths.toMutableList()
  }

  fun addTrustedPath(path: String) {
    state.trustedPaths.add(path)
  }
}