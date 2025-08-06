// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CodeInsightContexts")
package com.intellij.codeInsight.multiverse

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry

/**
 * Represents a context in which a code insight session runs for a given file or a set of files.
 * Is guaranteed to exist during one read action (to keep consistent PSI)
 *
 * @see CodeInsightContextManager
 */
interface CodeInsightContext

fun anyContext(): CodeInsightContext = AnyContext

fun defaultContext(): CodeInsightContext = DefaultContext

private object AnyContext : CodeInsightContext

private object DefaultContext : CodeInsightContext

fun isSharedSourceSupportEnabled(project: Project): Boolean {
  return CodeInsightContextManager.getInstance(project).isSharedSourceSupportEnabled
}

interface MultiverseEnabler {
  fun enableMultiverse(project: Project) : Boolean
}

fun isShowAllInheritorsEnabled(): Boolean {
  return Registry.`is`("intellij.platform.shared.source.line.markers.show.all.inheritors")
}