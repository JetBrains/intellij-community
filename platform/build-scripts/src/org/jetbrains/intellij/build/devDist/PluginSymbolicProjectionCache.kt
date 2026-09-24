// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devDist

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.JpsProject
import java.util.concurrent.ConcurrentHashMap

/**
 * The answers that every projection of [project] shares.
 *
 * A generator run projects thousands of layouts over one project, and each projection asks the same questions again:
 * whether a module is compatible with the frontend, and what a content module descriptor states. The answers depend on
 * the project and the descriptor text alone, so one instance holds them for the run. Concurrent projections may share it.
 */
@ApiStatus.Internal
class PluginSymbolicProjectionCache(@JvmField val project: JpsProject) {
  private val frontends = ConcurrentHashMap<Set<String>, FrontendCompatibility>()
  private val packageAttribute = ConcurrentHashMap<String, Boolean>()

  /** The frontend filter over the frontend [roots]. */
  fun frontend(roots: Set<String>): FrontendCompatibility {
    return frontends.computeIfAbsent(roots) { FrontendCompatibility(it, project::findModuleByName) }
  }

  /** Whether the root element of the content module descriptor [xml] declares a `package`. */
  fun hasPackageAttribute(xml: String): Boolean {
    return packageAttribute.computeIfAbsent(xml) { hasRootXmlAttribute(it, "package") }
  }
}
