// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.openapi.components.*
import com.intellij.util.io.isAncestor
import com.intellij.util.xmlb.annotations.OptionTag
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

@State(name = "Trusted.Paths.Settings", storages = [Storage(value = "trusted-paths.xml", roamingType = RoamingType.DISABLED)])
@Service(Service.Level.APP)
internal class TrustedPathsSettings : SimplePersistentStateComponent<TrustedPathsSettings.State>(State()) {

  class State : BaseState() {
    @get:OptionTag("TRUSTED_PATHS")
    var trustedPaths by list<String>()
  }

  fun isPathTrusted(path: Path): Boolean {
    return state.trustedPaths.map { Paths.get(it) }.any { it.isAncestor(path) }
  }

  fun getTrustedPaths(): List<String> = Collections.unmodifiableList(state.trustedPaths)

  fun setTrustedPaths(paths: List<String>) {
    state.trustedPaths = ArrayList<String>(paths)
  }

  fun addTrustedPath(path: String) {
    state.trustedPaths.add(path)
  }
}