// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl

import com.intellij.facet.*
import com.intellij.facet.impl.invalid.InvalidFacetManager
import com.intellij.facet.impl.invalid.InvalidFacetType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetManagerBridge
import org.jetbrains.jps.model.serialization.facet.FacetState
import java.util.*

private val LOG = Logger.getInstance(FacetTypeRegistryImpl::class.java)
private val FACET_TYPE_COMPARATOR = Comparator { o1: FacetType<*, *>, o2: FacetType<*, *> ->
  o1.presentableName.compareTo(o2.presentableName, ignoreCase = true)
}

private class FacetTypeRegistryImpl : FacetTypeRegistry() {
  private val typeIds = HashMap<String, FacetTypeId<*>>()
  private val facetTypes = HashMap<FacetTypeId<*>, FacetType<*, *>>()
  @Volatile private var extensionsLoaded = false
  private val typeRegistrationLock = Object()

  @Synchronized
  private fun registerFacetType(facetType: FacetType<*, *>) {
    val typeId = facetType.id
    val id = facetType.stringId
    LOG.assertTrue(!id.contains("/"), "Facet type id '$id' contains illegal character '/'")
    LOG.assertTrue(!facetTypes.containsKey(typeId), "Facet type '$id' is already registered")
    facetTypes[typeId] = facetType
    LOG.assertTrue(!typeIds.containsKey(id), "Facet type id '$id' is already registered")
    typeIds[id] = typeId
  }

  private fun loadInvalidFacetsOfType(project: Project, facetType: FacetType<*, *>) {
    val modulesWithFacets = InvalidFacetManager.getInstance(project).invalidFacets
      .filter { it.configuration.facetState.facetType == facetType.stringId }
      .mapTo(HashSet()) { it.module }
    for (module in modulesWithFacets) {
      val model = FacetManager.getInstance(module).createModifiableModel()
      val invalidFacets = model.getFacetsByType(InvalidFacetType.TYPE_ID).filter { it.configuration.facetState.facetType == facetType.stringId }
      for (invalidFacet in invalidFacets) {
        val newFacet = FacetUtil.createFacetFromStateRawJ(module, facetType, invalidFacet)
        model.replaceFacet(invalidFacet, newFacet)
        for (subFacet in invalidFacet.configuration.facetState.subFacets) {
          model.addFacet(FacetManagerBase.createInvalidFacet(module, subFacet, newFacet, invalidFacet.configuration.errorMessage, false, false))
        }
      }
      model.commit()
    }
  }

  @Synchronized
  private fun unregisterFacetType(facetType: FacetType<*, *>) {
    val id = facetType.id
    val stringId = facetType.stringId
    LOG.assertTrue(facetTypes.remove(id) != null, "Facet type '$stringId' is not registered")
    facetTypes.remove(id)
    typeIds.remove(stringId)
    ProjectManager.getInstance().openProjects.forEach {
      convertFacetsToInvalid(it, facetType)
    }
  }

  private fun convertFacetsToInvalid(project: Project, facetType: FacetType<*, *>) {
    val modulesWithFacets = ProjectFacetManager.getInstance(project).getFacets(facetType.id).mapTo(HashSet()) { it.module }
    for (module in modulesWithFacets) {
      val model = FacetManager.getInstance(module).createModifiableModel()
      val subFacets = model.allFacets.groupBy { it.underlyingFacet }
      val facets = model.getFacetsByType(facetType.id)
      for (facet in facets) {
        val facetState = saveFacetWithSubFacets(facet, subFacets) ?: continue
        val pluginName = facetType.pluginDescriptor?.name?.let { " '$it'" } ?: ""
        val errorMessage = ProjectBundle.message("error.message.plugin.for.facets.unloaded", pluginName, facetType.presentableName)
        val reportError = !ApplicationManager.getApplication().isUnitTestMode
        val invalidFacet = FacetManagerBase.createInvalidFacet(module, facetState, facet.underlyingFacet, errorMessage, true, reportError)
        removeAllSubFacets(model, facet, subFacets)
        model.replaceFacet(facet, invalidFacet)
      }
      model.commit()
    }
    if (modulesWithFacets.isNotEmpty()) {
      /* this is needed to recompute RootIndex; otherwise its DirectoryInfoImpl instances will keep references to SourceFolderBridges,
         which will keep references to Facet instances from unloaded plugin via WorkspaceEntityStorage making it impossible to unload plugins without a restart */
      ProjectRootManagerEx.getInstanceEx(project).makeRootsChange({}, RootsChangeRescanningInfo.NO_RESCAN_NEEDED)
    }
  }

  private fun removeAllSubFacets(model: ModifiableFacetModel, facet: Facet<*>, subFacets: Map<Facet<*>, List<Facet<*>>>) {
    val toRemove = subFacets[facet] ?: return
    for (subFacet in toRemove) {
      removeAllSubFacets(model, subFacet, subFacets)
      model.removeFacet(subFacet)
    }
  }

  private fun saveFacetWithSubFacets(facet: Facet<*>, subFacets: Map<Facet<FacetConfiguration>, List<Facet<*>>>): FacetState? {
    val state = FacetManagerBridge.saveFacetConfiguration(facet) ?: return null
    (subFacets[facet] ?: emptyList()).mapNotNullTo(state.subFacets) { saveFacetWithSubFacets(it, subFacets) }
    return state
  }

  @Synchronized
  override fun getFacetTypeIds(): Array<FacetTypeId<*>> {
    loadExtensions()
    return facetTypes.keys.toTypedArray()
  }

  @Synchronized
  override fun getFacetTypes(): Array<FacetType<*, *>> {
    loadExtensions()
    val facetTypes = facetTypes.values.toTypedArray()
    Arrays.sort(facetTypes, FACET_TYPE_COMPARATOR)
    return facetTypes
  }

  override fun getSortedFacetTypes(): Array<FacetType<*, *>> {
    val types = getFacetTypes()
    Arrays.sort(types, FACET_TYPE_COMPARATOR)
    return types
  }

  @Synchronized
  override fun findFacetType(id: String): FacetType<*, *>? {
    loadExtensions()
    val typeId = typeIds[id] ?: return null
    return facetTypes[typeId]
  }

  @Synchronized
  override fun <F : Facet<C>?, C : FacetConfiguration?> findFacetType(typeId: FacetTypeId<F>): FacetType<F, C> {
    loadExtensions()
    @Suppress("UNCHECKED_CAST")
    val type = facetTypes.get(typeId) as FacetType<F, C>?
    LOG.assertTrue(type != null, "Cannot find facet by id '$typeId'")
    return type!!
  }

  private fun loadExtensions() {
    if (extensionsLoaded) {
      return
    }

    synchronized(typeRegistrationLock) {
      if (extensionsLoaded) {
        return
      }

      //we cannot use forEachExtensionSafe here because it may throw ProcessCanceledException during iteration,
      // and we'll get a partially initialized state here
      for (type in FacetType.EP_NAME.extensionList) {
        registerFacetType(type)
      }
      FacetType.EP_NAME.addExtensionPointListener(
        object : ExtensionPointListener<FacetType<*, *>> {
          override fun extensionAdded(extension: FacetType<*, *>, pluginDescriptor: PluginDescriptor) {
            registerFacetType(extension)
            val projectManager = ProjectManager.getInstance()
            ApplicationManager.getApplication().runWriteAction {
              for (it in projectManager.openProjects) {
                loadInvalidFacetsOfType(it, extension)
              }
            }
          }

          override fun extensionRemoved(extension: FacetType<*, *>, pluginDescriptor: PluginDescriptor) {
            unregisterFacetType(extension)
          }
        }, null)
      extensionsLoaded = true
    }
  }
}