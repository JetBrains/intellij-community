// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.computeSafeIfAny
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project

/**
 * Allows to provide [SourcePosition] for a [NodeDescriptor].
 *
 * Used in Jump to Source action and inline debugger
 *
 * @see DefaultSourcePositionProvider
 */
abstract class SourcePositionProvider {
  protected open fun computeSourcePosition(
    descriptor: NodeDescriptor,
    project: Project,
    context: DebuggerContextImpl,
    nearest: Boolean,
  ): SourcePosition? = throw AbstractMethodError()

  protected open suspend fun computeSourcePositionAsync(
    descriptor: NodeDescriptor,
    project: Project,
    context: DebuggerContextImpl,
    nearest: Boolean,
  ): SourcePosition? = blockingContext {
    ReadAction.nonBlocking<SourcePosition> { computeSourcePosition(descriptor, project, context, nearest) }.executeSynchronously()
  }

  companion object {
    private val EP_NAME: ExtensionPointName<SourcePositionProvider> = ExtensionPointName.create("com.intellij.debugger.sourcePositionProvider")

    @JvmStatic
    @JvmOverloads
    suspend fun getSourcePosition(
      descriptor: NodeDescriptor,
      project: Project,
      context: DebuggerContextImpl,
      nearest: Boolean = false,
    ): SourcePosition? = computeSafeIfAny(EP_NAME) { provider ->
      try {
        provider.computeSourcePositionAsync(descriptor, project, context, nearest)
      }
      catch (_: IndexNotReadyException) {
        null
      }
    }
  }
}
