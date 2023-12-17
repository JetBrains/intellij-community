// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*

private val EP_NAME = ExtensionPointName<DynamicInspectionsProvider>("com.intellij.dynamicInspectionsProvider")

interface DynamicInspectionsProvider {
  fun inspectionsFlow(project: Project): Flow<Set<DynamicInspectionDescriptor>>
}

sealed class DynamicInspectionDescriptor {
  companion object {
    fun fromTool(tool: InspectionProfileEntry): DynamicInspectionDescriptor {
      return when(tool) {
        is LocalInspectionTool -> Local(tool)
        is GlobalInspectionTool -> Global(tool)
        else -> error("Got ${tool}, expected ${LocalInspectionTool::class.java} or ${GlobalInspectionTool::class.java}")
      }
    }
  }

  val toolWrapper: InspectionToolWrapper<*, *> by lazy {
    when(this) {
      is Global -> GlobalInspectionToolWrapper(tool)
      is Local -> LocalInspectionToolWrapper(tool)
    }
  }

  class Global(val tool: GlobalInspectionTool) : DynamicInspectionDescriptor()


  class Local(val tool: LocalInspectionTool) : DynamicInspectionDescriptor()
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun dynamicInspectionsFlow(project: Project): Flow<Set<DynamicInspectionDescriptor>> {
  val epUpdatedFlow = callbackFlow {
    trySendBlocking(Unit)

    val disposable = Disposer.newDisposable()
    val listener = object : ExtensionPointListener<DynamicInspectionsProvider> {
      override fun extensionAdded(extension: DynamicInspectionsProvider, pluginDescriptor: PluginDescriptor) {
        trySendBlocking(Unit)
      }

      override fun extensionRemoved(extension: DynamicInspectionsProvider, pluginDescriptor: PluginDescriptor) {
        trySendBlocking(Unit)
      }
    }
    EP_NAME.addExtensionPointListener(listener, disposable)
    awaitClose { Disposer.dispose(disposable) }
  }.buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_LATEST)

  return epUpdatedFlow
    .flatMapLatest {
      val allDynamicCustomInspectionsFlows: List<Flow<Set<DynamicInspectionDescriptor>>> = EP_NAME.extensionList.map { provider ->
        provider.inspectionsFlow(project)
          .map { inspections -> inspections.toSet() }
          .onStart { emit(emptySet()) }
      }
      combine(allDynamicCustomInspectionsFlows) {
        it.toList().flatten().toSet()
      }
    }.distinctUntilChanged()
}