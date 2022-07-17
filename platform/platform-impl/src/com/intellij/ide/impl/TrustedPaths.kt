// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.ide.impl

import com.intellij.openapi.components.*
import com.intellij.util.ThreeState
import com.intellij.util.io.isAncestor
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
@State(name = "Trusted.Paths", storages = [Storage(value = "trusted-paths.xml", roamingType = RoamingType.DISABLED)])
@Service(Service.Level.APP)
class TrustedPaths : SimplePersistentStateComponent<TrustedPaths.State>(State()) {
  companion object {
    @JvmStatic
    fun getInstance(): TrustedPaths = service()
  }

  class State : BaseState() {
    @get:OptionTag("TRUSTED_PROJECT_PATHS")
    val trustedPaths by map<String, Boolean>()
  }

  @ApiStatus.Internal
  fun getProjectPathTrustedState(path: Path): ThreeState {
    val trustedPaths = state.trustedPaths
    val ancestors = trustedPaths.keys.asSequence().map { path.fileSystem.getPath(it) }.filter { it.isAncestor(path) }
    val closestAncestor = ancestors.maxByOrNull { it.nameCount } ?: return ThreeState.UNSURE
    return when (trustedPaths[closestAncestor.pathString]) {
      true -> ThreeState.YES
      false -> ThreeState.NO
      null -> ThreeState.UNSURE
    }
  }

  @ApiStatus.Internal
  fun setProjectPathTrusted(path: Path, value: Boolean) {
    val newState = State()
    newState.trustedPaths.putAll(state.trustedPaths)
    newState.trustedPaths.put(path.pathString, value)
    loadState(newState)
  }
}