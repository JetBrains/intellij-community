// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.InspectionProfileConvertor
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.processOpenedProjects
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.serviceContainer.NonInjectable
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Paths

@State(name = "InspectionProfileManager",
       storages = [Storage("editor.xml")],
       additionalExportDirectory = InspectionProfileManager.INSPECTION_DIR)
open class ApplicationInspectionProfileManager @TestOnly @NonInjectable constructor(schemeManagerFactory: SchemeManagerFactory)
  : ApplicationInspectionProfileManagerBase(schemeManagerFactory), PersistentStateComponent<Element> {
  open val converter: InspectionProfileConvertor
    get() = InspectionProfileConvertor(this)

  val rootProfileName: String
    get() = schemeManager.currentSchemeName ?: DEFAULT_PROFILE_NAME

  constructor() : this(SchemeManagerFactory.getInstance())

  companion object {
    @JvmStatic
    fun getInstanceImpl() = service<InspectionProfileManager>() as ApplicationInspectionProfileManager

    private fun registerProvidedSeverities() {
      val map = HashMap<String, HighlightInfoType>()
      SeveritiesProvider.EP_NAME.forEachExtensionSafe { provider ->
        for (t in provider.severitiesHighlightInfoTypes) {
          val highlightSeverity = t.getSeverity(null)
          val icon = when (t) {
            is HighlightInfoType.Iconable -> {
              IconLoader.createLazy { (t as HighlightInfoType.Iconable).icon }
            }
            else -> null
          }
          map.put(highlightSeverity.name, t)
          HighlightDisplayLevel.registerSeverity(highlightSeverity, t.attributesKey, icon)
        }
      }
      if (map.isNotEmpty()) {
        SeverityRegistrar.registerStandard(map)
      }
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
    processOpenedProjects { project ->
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
    catch (ignored: Exception) {
      val file = Paths.get(path)
      ApplicationManager.getApplication().invokeLater({
                                                        Messages.showErrorDialog(
                                                          InspectionsBundle.message("inspection.error.loading.message", 0, file),
                                                          InspectionsBundle.message("inspection.errors.occurred.dialog.title"))
                                                      }, ModalityState.NON_MODAL)
    }

    return getProfile(path, false)
  }
}
