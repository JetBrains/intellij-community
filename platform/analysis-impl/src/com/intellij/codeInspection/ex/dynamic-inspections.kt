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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.onStart
import org.jetbrains.annotations.ApiStatus

private val EP_NAME = ExtensionPointName<DynamicInspectionsProvider>("com.intellij.dynamicInspectionsProvider")

@ApiStatus.Internal
interface DynamicInspectionsProvider {
  fun inspections(project: Project): Flow<Set<DynamicInspectionDescriptor>>
  fun name(): String
}

@ApiStatus.Internal
sealed class DynamicInspectionDescriptor(val providerName: String) {
  companion object {
    fun fromTool(tool: InspectionProfileEntry, providerName: String): DynamicInspectionDescriptor {
      return when(tool) {
        is LocalInspectionTool -> Local(tool, providerName)
        is GlobalInspectionTool -> Global(tool, providerName)
        else -> error("Got ${tool}, expected ${LocalInspectionTool::class.java} or ${GlobalInspectionTool::class.java}")
      }
    }

    fun fromLocalToolWithWrapper(
      tool: LocalInspectionTool,
      providerName: String,
      wrapperFactory: (LocalInspectionTool) -> InspectionToolWrapper<*,*>
    ): DynamicInspectionDescriptor {
      return LocalWithCustomWrapper(tool, providerName, wrapperFactory)
    }
  }


  abstract val toolWrapper: InspectionToolWrapper<*, *>

  class Local(val tool: LocalInspectionTool, providerName: String) : DynamicInspectionDescriptor(providerName) {
    override val toolWrapper: InspectionToolWrapper<*, *> by lazy {
      DynamicLocalInspectionToolWrapper(tool)
    }
  }

  class Global(val tool: GlobalInspectionTool, providerName: String) : DynamicInspectionDescriptor(providerName) {
    override val toolWrapper: InspectionToolWrapper<*, *> by lazy {
      DynamicGlobalInspectionToolWrapper(tool)
    }
  }

  class LocalWithCustomWrapper(
    val tool: LocalInspectionTool,
    providerName: String,
    private val wrapperFactory: (LocalInspectionTool) -> InspectionToolWrapper<*,*>
  ) : DynamicInspectionDescriptor(providerName) {
    override val toolWrapper: InspectionToolWrapper<*, *> by lazy {
      wrapperFactory(tool)
    }
  }

  private class DynamicLocalInspectionToolWrapper(tool: LocalInspectionTool) : LocalInspectionToolWrapper(tool) {
    override fun createCopy(): LocalInspectionToolWrapper = DynamicLocalInspectionToolWrapper(tool)
  }

  private class DynamicGlobalInspectionToolWrapper(tool: GlobalInspectionTool) : GlobalInspectionToolWrapper(tool) {
    override fun createCopy(): GlobalInspectionToolWrapper = DynamicGlobalInspectionToolWrapper(tool)
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun dynamicInspectionsFlow(project: Project): Flow<Set<DynamicInspectionDescriptor>> {
  val epUpdatedFlow = callbackFlow {
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
    .onStart { emit(Unit) }
    .flatMapLatest {
      val allDynamicInspectionsFlows: List<Flow<Set<DynamicInspectionDescriptor>>> = EP_NAME.extensionList.map { provider ->
        // if returned flow is simply empty, do not block collection of others in combine
        provider.inspections(project).onEmpty { emit(emptySet()) }
      }
      combine(allDynamicInspectionsFlows) {
        it.toList().flatten().toSet()
      }.onEmpty {
        // if there are no EP impls, emit the empty set
        emit(emptySet())
      }
    }.distinctUntilChanged()
}