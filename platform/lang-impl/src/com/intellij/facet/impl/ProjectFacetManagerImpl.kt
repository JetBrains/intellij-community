// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl

import com.intellij.ProjectTopics
import com.intellij.facet.*
import com.intellij.facet.impl.ProjectFacetManagerImpl
import com.intellij.facet.impl.ProjectFacetManagerImpl.ProjectFacetManagerState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Tag
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.util.concurrent.atomic.AtomicReference

@State(name = ProjectFacetManagerImpl.COMPONENT_NAME)
class ProjectFacetManagerImpl(private val myProject: Project) : ProjectFacetManagerEx(), PersistentStateComponent<ProjectFacetManagerState?> {
  private var myState = ProjectFacetManagerState()
  private val myIndex = AtomicReference<MultiMap<FacetTypeId<*>, Module>>()

  init {
    ProjectWideFacetListenersRegistry.getInstance(myProject).registerListener(object : ProjectWideFacetAdapter<Facet<*>?>() {
      override fun facetAdded(facet: Facet<*>) {
        myIndex.set(null)
      }

      override fun facetRemoved(facet: Facet<*>) {
        myIndex.set(null)
      }
    }, myProject)
    myProject.getMessageBus().connect().subscribe(ProjectTopics.MODULES, object : ModuleListener {
      override fun modulesAdded(project: Project, modules: List<Module>) {
        myIndex.set(null)
      }

      override fun moduleRemoved(project: Project, module: Module) {
        myIndex.set(null)
      }
    })
  }

  override fun getState(): ProjectFacetManagerState {
    return myState
  }

  override fun loadState(state: ProjectFacetManagerState) {
    myState = state
  }

  private val index: MultiMap<FacetTypeId<*>, Module>
    get() {
      val index = myIndex.get()
      return index ?: myIndex.updateAndGet { value: MultiMap<FacetTypeId<*>, Module>? -> value ?: createIndex() }
    }

  private fun createIndex(): MultiMap<FacetTypeId<*>, Module> {
    val index = MultiMap.createLinkedSet<FacetTypeId<*>, Module>()
    for (module in ModuleManager.getInstance(myProject).modules) {
      if (!module.isDisposed) {
        FacetManager.getInstance(module).getAllFacets()
          .map { it.typeId }
          .distinct()
          .forEach { index.putValue(it, module) }
      }
    }
    return index
  }

  override fun <F : Facet<*>?> getFacets(typeId: FacetTypeId<F>): List<F> {
    return ContainerUtil.concat(index[typeId]) { module: Module? ->
      FacetManager.getInstance(
        module!!).getFacetsByType(typeId)
    }
  }

  override fun getModulesWithFacet(typeId: FacetTypeId<*>): List<Module> {
    return index[typeId].toList()
  }

  override fun hasFacets(typeId: FacetTypeId<*>): Boolean {
    return index.containsKey(typeId)
  }

  override fun <F : Facet<*>?> getFacets(typeId: FacetTypeId<F>, modules: Array<Module>): List<F> {
    val result: MutableList<F> = ArrayList()
    for (module in modules) {
      result.addAll(FacetManager.getInstance(module).getFacetsByType(typeId))
    }
    return result
  }

  override fun <C : FacetConfiguration> createDefaultConfiguration(facetType: FacetType<*, C>): C {
    val configuration = facetType.createDefaultConfiguration()
    val state = myState.defaultConfigurations[facetType.stringId]
    if (state != null) {
      val defaultConfiguration = state.defaultConfiguration
      try {
        FacetUtil.loadFacetConfiguration(configuration, defaultConfiguration)
      }
      catch (e: InvalidDataException) {
        LOG.info(e)
      }
    }
    return configuration
  }

  override fun <C : FacetConfiguration> setDefaultConfiguration(facetType: FacetType<*, C>, configuration: C) {
    val defaultConfigurations = myState.defaultConfigurations
    var state = defaultConfigurations[facetType.stringId]
    if (state == null) {
      state = DefaultFacetConfigurationState()
      defaultConfigurations[facetType.stringId] = state
    }
    try {
      val element = FacetUtil.saveFacetConfiguration(configuration)
      state.defaultConfiguration = element
    }
    catch (e: WriteExternalException) {
      LOG.info(e)
    }
  }

  @Tag("default-facet-configuration")
  class DefaultFacetConfigurationState {
    @get:Tag("configuration")
    var defaultConfiguration: Element? = null
  }

  class ProjectFacetManagerState {
    @get:MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false,
                       keyAttributeName = "facet-type")
    @get:Tag("default-configurations")
    var defaultConfigurations: MutableMap<String, DefaultFacetConfigurationState> = HashMap()
  }

  companion object {
    const val COMPONENT_NAME: @NonNls String = "ProjectFacetManager"
    private val LOG = Logger.getInstance(ProjectFacetManagerImpl::class.java)
  }
}
