// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class ProjectInspectionToolRegistrar(private val project: Project, scope: CoroutineScope) : InspectionToolsSupplier() {
  companion object {
    fun getInstance(project: Project): ProjectInspectionToolRegistrar = project.service()
  }

  private val dynamicInspectionsFlow: StateFlow<Set<DynamicInspectionDescriptor>> = dynamicInspectionsFlow(project)
    .flowOn(Dispatchers.Default)
    .stateIn(scope, SharingStarted.Eagerly, initialValue = emptySet())

  init {
    InspectionToolRegistrar.getInstance()

    scope.launch(Dispatchers.Default) {
      var oldInspections = emptySet<DynamicInspectionDescriptor>()
      dynamicInspectionsFlow.collectLatest { currentInspections ->
        if (oldInspections == currentInspections) return@collectLatest

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
        DaemonCodeAnalyzerEx.getInstance(project).restart()
      }
    }
  }

  override fun createTools(): MutableList<InspectionToolWrapper<*, *>> {
    return (InspectionToolRegistrar.getInstance().createTools() + dynamicInspectionsFlow.value.map { it.toolWrapper }).toMutableList()
  }
}