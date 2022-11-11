// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.ide.impl

import com.intellij.openapi.components.*
import com.intellij.util.ThreeState
import com.intellij.util.io.isAncestor
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
@State(name = "Trusted.Paths", storages = [Storage(value = "trusted-paths.xml", roamingType = RoamingType.DISABLED)])
@Service(Service.Level.APP)
class TrustedPaths : SerializablePersistentStateComponent<TrustedPaths.State>(State()) {
  companion object {
    @JvmStatic
    fun getInstance(): TrustedPaths = service()
  }

  data class State(
    @JvmField
    @field:OptionTag("TRUSTED_PROJECT_PATHS")
    val trustedPaths: Map<String, Boolean> = emptyMap()
  )

  @ApiStatus.Internal
  fun getProjectPathTrustedState(path: Path): ThreeState {
    val trustedPaths = state.trustedPaths
    val ancestors = trustedPaths.keys.asSequence().map { path.fileSystem.getPath(it) }.filter { it.isAncestor(path) }
    val closestAncestor = ancestors.maxByOrNull { it.nameCount } ?: return ThreeState.UNSURE
    return when (trustedPaths[closestAncestor.toString()]) {
      true -> ThreeState.YES
      false -> ThreeState.NO
      null -> ThreeState.UNSURE
    }
  }

  @ApiStatus.Internal
  fun setProjectPathTrusted(path: Path, value: Boolean) {
    updateState { currentState ->
      State(currentState.trustedPaths + (path.toString() to value))
    }
  }
}