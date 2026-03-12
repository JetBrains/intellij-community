// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.codeInspection.ex

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.InspectionProfileConvertor
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.CoroutineScope
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Path

@State(name = "InspectionProfileManager",
       category = SettingsCategory.CODE,
       storages = [Storage(value = "editor.xml")],
       additionalExportDirectory = InspectionProfileManager.INSPECTION_DIR)
class ApplicationInspectionProfileManager @TestOnly @NonInjectable @Internal constructor(coroutineScope: CoroutineScope, schemeManagerFactory: SchemeManagerFactory) :
  ApplicationInspectionProfileManagerBase(schemeManagerFactory), PersistentStateComponent<Element> {

  val converter: InspectionProfileConvertor
    @Internal
    get() = InspectionProfileConvertor(this)

  val rootProfileName: String
    get() = schemeManager.currentSchemeName ?: InspectionProfile.DEFAULT_PROFILE_NAME

  @Suppress("TestOnlyProblems")
  @Internal
  constructor(coroutineScope: CoroutineScope) : this(coroutineScope, SchemeManagerFactory.getInstance())

  companion object {
    @JvmStatic
    @RequiresBlockingContext
    fun getInstanceImpl(): ApplicationInspectionProfileManager {
      return InspectionProfileManager.getInstance() as ApplicationInspectionProfileManager
    }
  }

  init {
    syncProvidedSeverities(notifyListeners = false)
    SeveritiesProvider.EP_NAME.point.addExtensionPointListener(coroutineScope, false, object : ExtensionPointListener<SeveritiesProvider> {
      override fun extensionAdded(extension: SeveritiesProvider, pluginDescriptor: PluginDescriptor) {
        syncProvidedSeverities(notifyListeners = true)
      }

      override fun extensionRemoved(extension: SeveritiesProvider, pluginDescriptor: PluginDescriptor) {
        syncProvidedSeverities(notifyListeners = true)
      }
    })
  }

  @TestOnly
  fun forceInitProfilesInTestUntil(disposable: Disposable) {
    LOAD_PROFILES = true
    profilesAreInitialized
    Disposer.register(disposable) {
      LOAD_PROFILES = false
    }
  }

  override fun getState(): Element {
    val state = Element("state")
    severityRegistrar.writeExternal(state)
    return state
  }

  override fun loadState(state: Element) {
    severityRegistrar.readExternal(state)
  }

  override fun fireProfileChanged(profile: InspectionProfileImpl) {
    for (project in getOpenedProjects()) {
      ProjectInspectionProfileManager.getInstance(project).fireProfileChanged(profile)
    }
  }

  @Throws(IOException::class, JDOMException::class)
  override fun loadProfile(path: String): InspectionProfileImpl? {
    try {
      return super.loadProfile(path)
    }
    catch (e: IOException) {
      throw e
    }
    catch (e: JDOMException) {
      throw e
    }
    catch (_: Exception) {
      val message = InspectionsBundle.message("inspection.error.loading.message", 0, Path.of(path))
      ApplicationManager.getApplication().invokeLater(
        { Messages.showErrorDialog(message, InspectionsBundle.message("inspection.errors.occurred.dialog.title")) },
        ModalityState.nonModal())
    }

    return getProfile(path, false)
  }

  private fun syncProvidedSeverities(notifyListeners: Boolean) {
    val providedSeverities = LinkedHashMap<String, HighlightDisplayLevel.SeverityDescriptor>()
    val providedTypes = LinkedHashMap<String, HighlightInfoType>()
    SeveritiesProvider.EP_NAME.forEachExtensionSafe { provider ->
      for (highlightInfoType in provider.severitiesHighlightInfoTypes) {
        val severity = highlightInfoType.getSeverity(null)
        val severityName = severity.name
        val iconable = highlightInfoType as? HighlightInfoType.Iconable
        // Keep provider icons lazy when the dynamic EP refresh rebuilds HighlightDisplayLevel state.
        // IconLoader.createLazy preserves icon-patcher/path-transform behavior, and reusing the same
        // lazy icon instance lets HighlightDisplayLevel expose identical icon/outlineIcon objects.
        val lazyIcon = iconable?.let { IconLoader.createLazy { it.icon } }
        providedSeverities.remove(severityName)
        providedTypes.remove(severityName)
        providedSeverities[severityName] = HighlightDisplayLevel.SeverityDescriptor(
          severity = severity,
          attributesKey = highlightInfoType.attributesKey,
          icon = lazyIcon,
        )
        // Preserve the original provider type instead of flattening it to HighlightInfoTypeImpl so
        // provider-specific contracts such as Iconable survive add/remove syncs.
        providedTypes[severityName] = highlightInfoType
      }
    }

    HighlightDisplayLevel.syncProvidedSeverities(providedSeverities)
    val removedSeverities = if (notifyListeners) SeverityRegistrar.syncProvidedSeverities(providedTypes)
    else SeverityRegistrar.syncProvidedSeveritiesSilently(providedTypes)
    if (notifyListeners && removedSeverities.isNotEmpty()) {
      normalizeRemovedProvidedSeverities(removedSeverities.toSet())
    }
  }

  private fun normalizeRemovedProvidedSeverities(removedSeverities: Set<String>) {
    val projectManagers = (ProjectManager.getInstanceIfCreated()?.openProjects ?: emptyArray()).asSequence().filterNot { it.isDisposed }.mapNotNull {
      it.getServiceIfCreated(InspectionProjectProfileManager::class.java) as? ProjectInspectionProfileManager
    }.toList()

    for (profile in normalizeRemovedSeverities(removedSeverities)) {
      for (projectManager in projectManagers) {
        projectManager.fireProfileChanged(profile)
      }
    }

    for (projectManager in projectManagers) {
      for (profile in projectManager.normalizeRemovedSeverities(removedSeverities)) {
        projectManager.fireProfileChanged(profile)
      }
    }
  }
}
