// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl

import com.intellij.ProjectTopics
import com.intellij.facet.*
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ModuleEx
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import java.util.*

internal class FacetEventsPublisher(private val project: Project) {
  private val facetsByType: MutableMap<FacetTypeId<*>, MutableMap<Facet<*>, Boolean>> = HashMap()
  private val manuallyRegisteredListeners = ContainerUtil.createConcurrentList<Pair<FacetTypeId<*>?, ProjectFacetListener<*>>>()

  init {
    val connection = project.messageBus.connect()
    connection.subscribe(ProjectTopics.MODULES, object : ModuleListener {
      override fun modulesAdded(project: Project, modules: List<Module>) {
        for (module in modules) {
          onModuleAdded(module)
        }
      }

      override fun moduleRemoved(project: Project, module: Module) {
        onModuleRemoved(module)
      }
    })
    for (module in ModuleManager.getInstance(project).modules) {
      onModuleAdded(module)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FacetEventsPublisher = project.service()

    @JvmField
    internal val LISTENER_EP = ExtensionPointName<ProjectFacetListenerEP>("com.intellij.projectFacetListener")
    private val LISTENER_EP_CACHE_KEY = java.util.function.Function<ProjectFacetListenerEP, String?> { it.facetTypeId }
    private const val ANY_TYPE = "any"
  }

  fun <F : Facet<*>> registerListener(type: FacetTypeId<F>?, listener: ProjectFacetListener<out F>) {
    manuallyRegisteredListeners += Pair(type, listener)
  }

  fun <F : Facet<*>> unregisterListener(type: FacetTypeId<F>?, listener: ProjectFacetListener<out F>) {
    manuallyRegisteredListeners -= Pair(type, listener)
  }

  fun fireBeforeFacetAdded(facet: Facet<*>) {
    getPublisher(facet.module).beforeFacetAdded(facet)
  }

  fun fireBeforeFacetRemoved(facet: Facet<*>) {
    getPublisher(facet.module).beforeFacetRemoved(facet)
    onFacetRemoved(facet, true)
  }

  fun fireBeforeFacetRenamed(facet: Facet<*>) {
    getPublisher(facet.module).beforeFacetRenamed(facet)
  }

  fun fireFacetAdded(facet: Facet<*>) {
    getPublisher(facet.module).facetAdded(facet)
    onFacetAdded(facet)
  }

  fun fireFacetRemoved(module: Module, facet: Facet<*>) {
    getPublisher(module).facetRemoved(facet)
    onFacetRemoved(facet, false)
  }

  fun fireFacetRenamed(facet: Facet<*>, oldName: String) {
    getPublisher(facet.module).facetRenamed(facet, oldName)
  }

  fun fireFacetConfigurationChanged(facet: Facet<*>) {
    getPublisher(facet.module).facetConfigurationChanged(facet)
    onFacetChanged(facet)
  }

  private fun onModuleRemoved(module: Module) {
    for (facet in FacetManager.getInstance(module).allFacets) {
      onFacetRemoved(facet, false)
    }
  }

  private fun onModuleAdded(module: Module) {
    for (facet in FacetManager.getInstance(module).allFacets) {
      onFacetAdded(facet)
    }
  }

  private fun <F : Facet<*>> onFacetRemoved(facet: F, before: Boolean) {
    val typeId = facet.typeId
    val facets = facetsByType[typeId]
    val lastFacet: Boolean
    if (facets != null) {
      facets.remove(facet)
      lastFacet = facets.isEmpty()
      if (lastFacet) {
        facetsByType.remove(typeId)
      }
    }
    else {
      lastFacet = true
    }
    processListeners(facet.type) {
      if (before) {
        it.beforeFacetRemoved(facet)
      }
      else {
        it.facetRemoved(facet, project)
        if (lastFacet) {
          it.allFacetsRemoved(project)
        }
      }
    }
    processListeners {
      if (before) {
        it.beforeFacetRemoved(facet)
      }
      else {
        it.facetRemoved(facet, project)
        if (facetsByType.isEmpty()) {
          it.allFacetsRemoved(project)
        }
      }
    }
  }

  private fun <F : Facet<*>> onFacetAdded(facet: F) {
    val firstFacet = facetsByType.isEmpty()
    val typeId = facet.typeId
    var facets = facetsByType[typeId]
    if (facets == null) {
      facets = WeakHashMap()
      facetsByType[typeId] = facets
    }
    val firstFacetOfType = facets.isEmpty()
    facets[facet] = true
    processListeners {
      if (firstFacet) {
        it.firstFacetAdded(project)
      }
      it.facetAdded(facet)
    }
    processListeners(facet.type) {
      if (firstFacetOfType) {
        it.firstFacetAdded(project)
      }
      it.facetAdded(facet)
    }
  }

  private fun <F : Facet<*>> onFacetChanged(facet: F) {
    processListeners(facet.type) {
      it.facetConfigurationChanged(facet)
    }
    processListeners {
      it.facetConfigurationChanged(facet)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private inline fun <F : Facet<*>> processListeners(facetType: FacetType<F, *>, action: (ProjectFacetListener<F>) -> Unit) {
    for (listenerEP in LISTENER_EP.getByGroupingKey(facetType.stringId, LISTENER_EP_CACHE_KEY::class.java, LISTENER_EP_CACHE_KEY)) {
      action(listenerEP.listenerInstance as ProjectFacetListener<F>)
    }
    manuallyRegisteredListeners.filter { it.first == facetType.id }.forEach {
      action(it.second as ProjectFacetListener<F>)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private inline fun processListeners(action: (ProjectFacetListener<Facet<*>>) -> Unit) {
    for (listenerEP in LISTENER_EP.getByGroupingKey(ANY_TYPE, LISTENER_EP_CACHE_KEY::class.java, LISTENER_EP_CACHE_KEY)) {
      action(listenerEP.listenerInstance as ProjectFacetListener<Facet<*>>)
    }
    manuallyRegisteredListeners.asSequence()
      .filter { it.first == null }
      .forEach {
        action(it.second as ProjectFacetListener<Facet<*>>)
      }
  }

  private fun getPublisher(module: Module): FacetManagerListener {
    @Suppress("DEPRECATION")
    return ((module as? ModuleEx)?.deprecatedModuleLevelMessageBus ?: module.messageBus).syncPublisher(FacetManager.FACETS_TOPIC)
  }
}
