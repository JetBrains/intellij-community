// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

/**
 * Community platform features materialized as generated bundled plugin wrappers.
 */
@Suppress("unused")
object CommunityPlatformPlugins {
  fun recentFiles(): ModuleSet = plugin("recentFiles") {
    module("intellij.platform.recentFiles")
    module("intellij.platform.recentFiles.frontend")
    module("intellij.platform.recentFiles.backend")
  }

  fun servicesView(): ModuleSet = plugin("servicesView") {
    module("intellij.platform.clouds")
    module("intellij.platform.execution.serviceView")
    module("intellij.platform.execution.serviceView.frontend")
    module("intellij.platform.execution.serviceView.backend")
    module("intellij.platform.execution.dashboard")
    module("intellij.platform.execution.dashboard.backend")
    module("intellij.platform.execution.dashboard.frontend")
  }

  fun structureView(): ModuleSet = plugin("structureView") {
    module("intellij.platform.structureView.impl")
    module("intellij.platform.structureView.backend")
    module("intellij.platform.structureView.frontend")
  }

  fun todoView(): ModuleSet = plugin("todoView") {
    module("intellij.platform.todo")
    module("intellij.platform.todo.backend")
    module("intellij.platform.vcs.impl.lang.todo")
  }

  fun vcsFrontend(): ModuleSet = plugin("vcs.frontend") {
    module("intellij.platform.vcs.impl.frontend")
  }

  fun structuralSearch(): ModuleSet = plugin("structuralSearch") {
    module("intellij.platform.structuralSearch")
  }

  fun debuggerStreams(): ModuleSet = plugin("debugger.streams", addToMainModule = false) {
    module("intellij.debugger.streams.core")
    module("intellij.debugger.streams.shared")
    module("intellij.debugger.streams.backend")
  }

  fun gridCore(): ModuleSet = plugin("grid.core") {
    module("intellij.grid")
    module("intellij.grid.types")
    module("intellij.grid.csv.core.impl")
    module("intellij.grid.core.impl")
    module("intellij.grid.impl")
    module("intellij.grid.impl.ide")
  }
}
