// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.facet.impl

import com.intellij.facet.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil

private val LISTENER_EP = ExtensionPointName<ProjectFacetListenerEP>("com.intellij.projectFacetListener")
private val LISTENER_EP_CACHE_KEY = java.util.function.Function<ProjectFacetListenerEP, String?> { it.facetTypeId }
private const val ANY_TYPE = "any"

@Service(Service.Level.PROJECT)
internal class FacetEventsPublisher(private val project: Project) {
  private val facetsByType = HashMap<FacetTypeId<*>, MutableSet<Facet<*>>>()
  private val manuallyRegisteredListeners = ContainerUtil.createConcurrentList<Pair<FacetTypeId<*>?, ProjectFacetListener<*>>>()
  private val publisher = project.messageBus.syncPublisher(FacetManager.FACETS_TOPIC)

  companion object {
    fun getInstance(project: Project): FacetEventsPublisher = project.service()
  }

  internal fun sendEvents(facetManagerFactory: FacetManagerFactoryImpl) {
    val listeners by lazy { getAnyFacetListeners() }
    for (facetManager in facetManagerFactory.getAllFacets()) {
      for (facet in facetManager.allFacets) {
        onFacetAdded(facet, listeners)
      }
    }
  }

  internal fun listen() {
    val connection = project.messageBus.simpleConnect()
    connection.subscribe(ModuleListener.TOPIC, object : ModuleListener {
      override fun modulesAdded(project: Project, modules: List<Module>) {
        val facetManagerFactory = project.service<FacetManagerFactory>()
        val listeners by lazy { getAnyFacetListeners() }
        for (module in modules) {
          for (facet in facetManagerFactory.getFacetManager(module).allFacets) {
            onFacetAdded(facet, listeners)
          }
        }
      }

      override fun moduleRemoved(project: Project, module: Module) {
        project.service<FacetEventsPublisher>().onModuleRemoved(module)
      }
    })
  }

  fun <F : Facet<*>> registerListener(type: FacetTypeId<F>?, listener: ProjectFacetListener<out F>) {
    manuallyRegisteredListeners.add(Pair(type, listener))
  }

  fun <F : Facet<*>> unregisterListener(type: FacetTypeId<F>?, listener: ProjectFacetListener<out F>) {
    manuallyRegisteredListeners.remove(Pair(type, listener))
  }

  fun fireBeforeFacetAdded(facet: Facet<*>) {
    publisher.beforeFacetAdded(facet)
  }

  fun fireBeforeFacetRemoved(facet: Facet<*>) {
    publisher.beforeFacetRemoved(facet)
    onFacetRemoved(facet, true)
  }

  fun fireBeforeFacetRenamed(facet: Facet<*>) {
    publisher.beforeFacetRenamed(facet)
  }

  fun fireFacetAdded(facet: Facet<*>) {
    publisher.facetAdded(facet)
    onFacetAdded(facet, getAnyFacetListeners())
  }

  fun fireFacetRemoved(facet: Facet<*>) {
    publisher.facetRemoved(facet)
    onFacetRemoved(facet, false)
  }

  fun fireFacetRenamed(facet: Facet<*>, oldName: String) {
    publisher.facetRenamed(facet, oldName)
  }

  fun fireFacetConfigurationChanged(facet: Facet<*>) {
    publisher.facetConfigurationChanged(facet)
    onFacetChanged(facet)
  }

  private fun onModuleRemoved(module: Module) {
    for (facet in FacetManager.getInstance(module).allFacets) {
      onFacetRemoved(facet, false)
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
    processListeners(getAnyFacetListeners()) {
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

  private fun <F : Facet<*>> onFacetAdded(facet: F, listeners: List<ProjectFacetListenerEP>) {
    val isFirstFacet = facetsByType.isEmpty()
    val facets = facetsByType.computeIfAbsent(facet.typeId) { ContainerUtil.createWeakSet() }

    val isFirstFacetOfType = facets.isEmpty()
    facets.add(facet)

    processListeners(listeners) {
      if (isFirstFacet) {
        it.firstFacetAdded(project)
      }
      it.facetAdded(facet)
    }
    processListeners(facet.type) {
      if (isFirstFacetOfType) {
        it.firstFacetAdded(project)
      }
      it.facetAdded(facet)
    }
  }

  private fun <F : Facet<*>> onFacetChanged(facet: F) {
    processListeners(facet.type) {
      it.facetConfigurationChanged(facet)
    }
    processListeners(listeners = getAnyFacetListeners()) {
      it.facetConfigurationChanged(facet)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private inline fun <F : Facet<*>> processListeners(facetType: FacetType<F, *>, action: (ProjectFacetListener<F>) -> Unit) {
    for (listenerEP in LISTENER_EP.getByGroupingKey(facetType.stringId, LISTENER_EP_CACHE_KEY::class.java, LISTENER_EP_CACHE_KEY)) {
      action(listenerEP.listenerInstance as ProjectFacetListener<F>)
    }

    for (pair in manuallyRegisteredListeners) {
      if (pair.first == facetType.id) {
        action(pair.second as ProjectFacetListener<F>)
      }
    }
  }

  private fun processListeners(listeners: List<ProjectFacetListenerEP>, action: (ProjectFacetListener<Facet<*>>) -> Unit) {
    for (extension in listeners) {
      @Suppress("UNCHECKED_CAST")
      action(extension.listenerInstance as ProjectFacetListener<Facet<*>>)
    }

    for (pair in manuallyRegisteredListeners) {
      if (pair.first == null) {
        @Suppress("UNCHECKED_CAST")
        action(pair.second as ProjectFacetListener<Facet<*>>)
      }
    }
  }

  private fun getAnyFacetListeners(): List<ProjectFacetListenerEP> {
    return LISTENER_EP.getByGroupingKey(ANY_TYPE, LISTENER_EP_CACHE_KEY::class.java, LISTENER_EP_CACHE_KEY)
  }
}
