// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.local

import com.intellij.ide.starters.shared.CommonStarterContext

class StarterContext : CommonStarterContext() {
  lateinit var starterPack: StarterPack
  var starter: Starter? = null
  var starterDependencyConfig: DependencyConfig? = null
  val startersDependencyUpdates: MutableMap<String, DependencyConfig> = mutableMapOf()

  val libraryIds: MutableSet<String> = HashSet()
}