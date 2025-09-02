// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl.ui

import com.intellij.facet.*
import com.intellij.facet.ui.FacetDependentToolWindow
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.toolWindow.RegisterToolWindowTaskProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class FacetDependentToolWindowManager : RegisterToolWindowTaskProvider {
  override suspend fun getTasks(project: Project): Collection<ToolWindowEP> {
    val facetDependentToolWindows = FacetDependentToolWindow.EXTENSION_POINT_NAME.extensionList
    if (facetDependentToolWindows.isEmpty()) {
      return emptyList()
    }

    val projectFacetManager = project.serviceAsync<ProjectFacetManager>()
    val projectWideFacetListenersRegistry = project.serviceAsync<ProjectWideFacetListenersRegistry>()
    return withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val result = ArrayList<ToolWindowEP>()
      l@ for (extension in facetDependentToolWindows) {
        for (type in extension.getFacetTypes()) {
          if (projectFacetManager.hasFacets(type.getId())) {
            result.add(extension)
            continue@l
          }
        }
      }

      projectOpened(project, projectWideFacetListenersRegistry)
      result
    }
  }
}

private fun projectOpened(project: Project, projectWideFacetListenersRegistry: ProjectWideFacetListenersRegistry) {
  projectWideFacetListenersRegistry.registerListener(object : ProjectWideFacetAdapter<Facet<*>?>() {
    override fun facetAdded(facet: Facet<*>) {
      checkIfToolwindowMustBeAdded(facet.getType())
    }

    override fun facetRemoved(facet: Facet<*>) {
      checkIfToolwindowMustBeRemoved(facet.getType())
    }

    fun checkIfToolwindowMustBeAdded(facetType: FacetType<*, *>) {
      val toolWindowManager = ToolWindowManager.getInstance(project)
      toolWindowManager.invokeLater {
        for (extension in getDependentExtensions(facetType)) {
          ensureToolWindowExists(extension, toolWindowManager)
        }
      }
    }

    fun checkIfToolwindowMustBeRemoved(removedFacetType: FacetType<*, *>) {
      val toolWindowManager = ToolWindowManager.getInstance(project)
      toolWindowManager.invokeLater(Runnable {
        val facetManager = ProjectFacetManager.getInstance(project)
        if (facetManager.hasFacets(removedFacetType.getId())) {
          return@Runnable
        }
        for (extension in getDependentExtensions(removedFacetType)) {
          val toolWindow = toolWindowManager.getToolWindow(extension.id)
          if (toolWindow != null) {
            // check for other facets
            for (facetType in extension.getFacetTypes()) {
              if (facetManager.hasFacets(facetType.getId())) {
                return@Runnable
              }
            }
            toolWindow.remove()
          }
        }
      })
    }
  }, project)

  FacetDependentToolWindow.EXTENSION_POINT_NAME.addExtensionPointListener(object : ExtensionPointListener<FacetDependentToolWindow> {
    override fun extensionAdded(extension: FacetDependentToolWindow, pluginDescriptor: PluginDescriptor) {
      initToolWindowIfNeeded(extension, project)
    }

    override fun extensionRemoved(extension: FacetDependentToolWindow, pluginDescriptor: PluginDescriptor) {
      ToolWindowManager.getInstance(project).getToolWindow(extension.id)?.remove()
    }
  }, project)
}

private fun initToolWindowIfNeeded(extension: FacetDependentToolWindow, project: Project) {
  val projectFacetManager = ProjectFacetManager.getInstance(project)
  for (type in extension.getFacetTypes()) {
    if (projectFacetManager.hasFacets(type.getId())) {
      val toolWindowManager = ToolWindowManager.getInstance(project)
      ensureToolWindowExists(extension, toolWindowManager)
      return
    }
  }
}

private fun ensureToolWindowExists(extension: FacetDependentToolWindow, toolWindowManager: ToolWindowManager) {
  if (toolWindowManager.getToolWindow(extension.id) != null) {
    return
  }

  (toolWindowManager as ToolWindowManagerImpl).initToolWindow(extension)

  if (!extension.showOnStripeByDefault) {
    val toolWindow = toolWindowManager.getToolWindow(extension.id) ?: return
    val windowInfo = toolWindowManager.getLayout().getInfo(extension.id)
    if (windowInfo != null && !windowInfo.isFromPersistentSettings) {
      toolWindow.setShowStripeButton(false)
    }
  }
}

private fun getDependentExtensions(facetType: FacetType<*, *>): Sequence<FacetDependentToolWindow> {
  return FacetDependentToolWindow.EXTENSION_POINT_NAME.extensionList
    .asSequence()
    .filter {
      it.facetIds.contains(facetType.stringId)
    }
}
