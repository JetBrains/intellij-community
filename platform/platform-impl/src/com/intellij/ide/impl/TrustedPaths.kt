// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.ThreeState
import com.intellij.util.io.isAncestor
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.nio.file.Paths

@ApiStatus.Internal
@State(name = "Trusted.Paths", storages = [Storage(value = "trusted-paths.xml", roamingType = RoamingType.DISABLED)])
@Service(Service.Level.APP)
internal class TrustedPaths : SimplePersistentStateComponent<TrustedPaths.State>(State()) {

  class State : BaseState() {
    @get:OptionTag("TRUSTED_PROJECT_PATHS")
    var trustedPaths by map<String, Boolean>()
  }

  private val trustedPaths get() = state.trustedPaths.filterValues { it }.keys.map { Paths.get(it) }
  private val notTrustedPaths get() = state.trustedPaths.filterValues { !it }.keys.map { Paths.get(it) }

  @ApiStatus.Internal
  fun getProjectPathTrustedState(path: Path): ThreeState {
    if (notTrustedPaths.any { it.isAncestor(path) }) return ThreeState.NO
    if (trustedPaths.any { it.isAncestor(path) }) return ThreeState.YES
    return ThreeState.UNSURE
  }

  @ApiStatus.Internal
  fun setProjectPathTrusted(path: Path, value: Boolean) {
    state.trustedPaths[path.toString()] = value
  }

  companion object {
    fun getInstance(): TrustedPaths = ApplicationManager.getApplication().getService(TrustedPaths::class.java)
  }
}