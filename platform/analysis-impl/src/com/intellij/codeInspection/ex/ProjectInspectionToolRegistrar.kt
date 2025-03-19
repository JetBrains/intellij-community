// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class ProjectInspectionToolRegistrar(project: Project, scope: CoroutineScope) : InspectionToolsSupplier(), Disposable {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectInspectionToolRegistrar = project.service()
  }

  private val dynamicInspectionsFlow: StateFlow<Set<DynamicInspectionDescriptor>?> = dynamicInspectionsFlow(project)
    .flowOn(Dispatchers.Default)
    .stateIn(scope, SharingStarted.Lazily, initialValue = null)

  private val dynamicInspectionsWereInitialized = Job()

  private val updateInspectionProfilesSubscription = scope.launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
    var oldInspections = emptySet<DynamicInspectionDescriptor>()
    dynamicInspectionsFlow
      .filterNotNull()
      .collect { currentInspections ->
        try {
          if (oldInspections == currentInspections) return@collect

          val newInspections = currentInspections - oldInspections
          val outdatedInspections = oldInspections - currentInspections

          listeners.forEach { listener ->
            outdatedInspections.forEach {
              listener.toolRemoved(it.toolWrapper)
            }
          }
          listeners.forEach { listener ->
            newInspections.forEach {
              listener.toolAdded(it.toolWrapper)
            }
          }
          oldInspections = currentInspections
          DaemonCodeAnalyzerEx.getInstanceEx(project).restart("ProjectInspectionToolRegistrar.updateInspectionProfilesSubscription")
        }
        finally {
          dynamicInspectionsWereInitialized.complete()
        }
      }
  }

  init {
    // Propagate all events from app registrar
    InspectionToolRegistrar.getInstance().addListener(
      object : Listener {
        override fun toolAdded(inspectionTool: InspectionToolWrapper<*, *>) {
          listeners.forEach {
            it.toolAdded(inspectionTool)
          }
        }

        override fun toolRemoved(inspectionTool: InspectionToolWrapper<*, *>) {
          listeners.forEach {
            it.toolRemoved(inspectionTool)
          }
        }
      }, this
    )
  }

  suspend fun waitForDynamicInspectionsInitialization() {
    updateInspectionProfilesSubscription.start()
    dynamicInspectionsWereInitialized.join()
  }

  override fun createTools(): List<InspectionToolWrapper<*, *>> {
    updateInspectionProfilesSubscription.start()
    val dynamicTools = dynamicInspectionsFlow.value?.map { it.toolWrapper } ?: emptyList()
    return InspectionToolRegistrar.getInstance().createTools() + dynamicTools
  }
}