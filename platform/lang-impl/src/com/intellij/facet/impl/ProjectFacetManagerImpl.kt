// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl

import com.intellij.facet.*
import com.intellij.facet.impl.ProjectFacetManagerImpl.ProjectFacetManagerState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.util.containers.MultiMap
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetModelBridge.Companion.facetMapping
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.util.concurrent.atomic.AtomicReference

@State(name = ProjectFacetManagerImpl.COMPONENT_NAME)
class ProjectFacetManagerImpl(private val myProject: Project) : ProjectFacetManager(), PersistentStateComponent<ProjectFacetManagerState> {
  private var myState = ProjectFacetManagerState()
  private val myIndex = AtomicReference<MultiMap<FacetTypeId<*>, Module>>()

  init {
    ProjectWideFacetListenersRegistry.getInstance(myProject).registerListener(object : ProjectWideFacetAdapter<Facet<*>>() {
      override fun facetAdded(facet: Facet<*>) {
        myIndex.set(null)
      }

      override fun facetRemoved(facet: Facet<*>) {
        myIndex.set(null)
      }
    }, myProject)
    myProject.getMessageBus().connect().subscribe(ModuleListener.TOPIC, object : ModuleListener {
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
      return index ?: myIndex.updateAndGet { it ?: createIndex() }
    }

  private fun createIndex(): MultiMap<FacetTypeId<*>, Module> {
    val index = MultiMap.createLinkedSet<FacetTypeId<*>, Module>()
    WorkspaceModel.getInstance(myProject).currentSnapshot.facetMapping().forEach { _, facet ->
      index.putValue(facet.typeId, facet.module)
    }
    return index
  }

  private fun <F : Facet<*>> getFacets(typeId: FacetTypeId<F>, modules: Collection<Module>): List<F> {
    return modules.distinct().filter { !it.isDisposed }.flatMap {
      FacetManager.getInstance(it).getFacetsByType(typeId)
    }.toList()
  }

  override fun <F : Facet<*>> getFacets(typeId: FacetTypeId<F>): List<F> = getFacets(typeId, index[typeId])

  override fun <F : Facet<*>> getFacets(typeId: FacetTypeId<F>, modules: Array<Module>)= getFacets(typeId, modules.toList())

  override fun getModulesWithFacet(typeId: FacetTypeId<*>): List<Module> {
    return index[typeId].toList()
  }

  override fun hasFacets(typeId: FacetTypeId<*>): Boolean {
    return index.containsKey(typeId)
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
