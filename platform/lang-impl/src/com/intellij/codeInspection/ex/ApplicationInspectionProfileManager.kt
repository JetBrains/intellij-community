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
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.serviceContainer.NonInjectable
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Path

@State(name = "InspectionProfileManager",
       category = SettingsCategory.CODE,
       storages = [Storage(value = "editor.xml")],
       additionalExportDirectory = InspectionProfileManager.INSPECTION_DIR)
open class ApplicationInspectionProfileManager @TestOnly @NonInjectable constructor(schemeManagerFactory: SchemeManagerFactory)
  : ApplicationInspectionProfileManagerBase(schemeManagerFactory), PersistentStateComponent<Element> {

  open val converter: InspectionProfileConvertor
    @ApiStatus.Internal
    get() = InspectionProfileConvertor(this)

  val rootProfileName: String
    get() = schemeManager.currentSchemeName ?: InspectionProfile.DEFAULT_PROFILE_NAME

  @Suppress("TestOnlyProblems")
  constructor() : this(SchemeManagerFactory.getInstance())

  companion object {
    @JvmStatic
    fun getInstanceImpl(): ApplicationInspectionProfileManager {
      return InspectionProfileManager.getInstance() as ApplicationInspectionProfileManager
    }
  }

  init {
    registerProvidedSeverities()
  }

  @TestOnly
  fun forceInitProfilesInTestUntil(disposable: Disposable) {
    LOAD_PROFILES = true
    profilesAreInitialized
    Disposer.register(disposable) {
      LOAD_PROFILES = false
    }
  }

  override fun getState(): Element? {
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
    catch (e: IOException) { throw e }
    catch (e: JDOMException) { throw e }
    catch (ignored: Exception) {
      val message = InspectionsBundle.message("inspection.error.loading.message", 0, Path.of(path))
      ApplicationManager.getApplication().invokeLater(
        { Messages.showErrorDialog(message, InspectionsBundle.message("inspection.errors.occurred.dialog.title")) },
        ModalityState.nonModal())
    }

    return getProfile(path, false)
  }
}

private fun registerProvidedSeverities() {
  val map = HashMap<String, HighlightInfoType>()
  SeveritiesProvider.EP_NAME.forEachExtensionSafe { provider ->
    for (t in provider.severitiesHighlightInfoTypes) {
      val highlightSeverity = t.getSeverity(null)
      val icon = if (t is HighlightInfoType.Iconable) IconLoader.createLazy { t.icon } else null
      map.put(highlightSeverity.name, t)
      HighlightDisplayLevel.registerSeverity(highlightSeverity, t.attributesKey, icon)
    }
  }
  if (map.isNotEmpty()) {
    SeverityRegistrar.registerStandard(map)
  }
}