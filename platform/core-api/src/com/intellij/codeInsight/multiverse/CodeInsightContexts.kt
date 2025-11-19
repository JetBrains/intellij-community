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

/**
 * Placeholder context that signals that you, as an API client, don't care about the context,
 * so the API provider can choose any context.
 *
 * # Examples:
 *
 * This call will return a PSI file for the given virtual file that is either built already, or with the preferred context of the given virtualFile.
 * ```kotlin
 *   PsiManager.getInstance(project).findFile(virtualFile, anyContext())
 * ```
 *
 * On the contrary, if you use [defaultContext], you'll get a PSI file for the given virtual file that is **built with the default context**:
 * ```kotlin
 *   PsiManager.getInstance(project).findFile(virtualFile, defaultContext())
 * ```
 */
fun anyContext(): CodeInsightContext = AnyContext

/**
 * Default context is used when there is no context is available for the given file.
 *
 * If you don't know/care if there is another context available, use [anyContext] instead.
 *
 * @see anyContext
 */
fun defaultContext(): CodeInsightContext = DefaultContext

private object AnyContext : CodeInsightContext {
  override fun toString(): String = "AnyContext"
}

private object DefaultContext : CodeInsightContext {
  override fun toString(): String = "DefaultContext"
}

fun isSharedSourceSupportEnabled(project: Project): Boolean =
  isSharedSourceSupportEnabledImpl(project)

interface MultiverseEnabler {
  fun enableMultiverse(project: Project) : Boolean
}

fun isShowAllInheritorsEnabled(): Boolean {
  return Registry.`is`("intellij.platform.shared.source.line.markers.show.all.inheritors")
}