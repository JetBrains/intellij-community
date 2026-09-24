// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.JpsProject
import java.util.concurrent.ConcurrentHashMap

/**
 * The answers that every projection of [project] shares.
 *
 * A generator run projects thousands of layouts over one project, and each projection asks again whether a module is
 * compatible with the frontend. The answer depends on the project and the frontend roots alone, so one instance holds
 * it for the run. Concurrent projections may share it.
 */
@ApiStatus.Internal
class PluginSymbolicProjectionCache(@JvmField val project: JpsProject) {
  private val frontends = ConcurrentHashMap<Set<String>, FrontendCompatibility>()

  /** The frontend filter over the frontend [roots]. */
  fun frontend(roots: Set<String>): FrontendCompatibility {
    return frontends.computeIfAbsent(roots) { FrontendCompatibility(it, project::findModuleByName) }
  }
}
