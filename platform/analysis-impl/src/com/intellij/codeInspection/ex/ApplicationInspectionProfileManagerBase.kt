// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.codeInspection.InspectionProfile
import com.intellij.configurationStore.BundledSchemeEP
import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.profile.ProfileChangeAdapter
import com.intellij.profile.codeInspection.BaseInspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProfileLoadUtil
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProfileProcessor
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.serviceContainer.NonInjectable
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.Function

open class ApplicationInspectionProfileManagerBase @Internal @NonInjectable constructor(schemeManagerFactory: SchemeManagerFactory) :
  BaseInspectionProfileManager(ApplicationManager.getApplication().messageBus) {

  @Suppress("unused")
  constructor() : this(SchemeManagerFactory.getInstance())

  init {
    val app = ApplicationManager.getApplication()
    app.messageBus.connect().subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      @Suppress("removal", "OVERRIDE_DEPRECATION")
      override fun projectOpened(project: Project) {
        val appScopeListener = NamedScopesHolder.ScopeListener {
          profiles.forEach { it.scopesChanged() }
        }
        NamedScopeManager.getInstance(project).addScopeListener(appScopeListener, project)
      }
    })
  }

  override val schemeManager: SchemeManager<InspectionProfileImpl> =
    schemeManagerFactory.create(InspectionProfileManager.INSPECTION_DIR, object : InspectionProfileProcessor() {
      override fun getSchemeKey(attributeProvider: Function<String, String?>, fileNameWithoutExtension: String) = fileNameWithoutExtension

      override fun createScheme(dataHolder: SchemeDataHolder<InspectionProfileImpl>,
                                name: String,
                                attributeProvider: (String) -> String?,
                                isBundled: Boolean): InspectionProfileImpl =
        InspectionProfileImpl(name, InspectionToolRegistrar.getInstance(), this@ApplicationInspectionProfileManagerBase, dataHolder)

      override fun onSchemeAdded(scheme: InspectionProfileImpl) {
        fireProfileChanged(scheme)
      }

      override fun onCurrentSchemeSwitched(oldScheme: InspectionProfileImpl?,
                                           newScheme: InspectionProfileImpl?,
                                           processChangeSynchronously: Boolean) {
        DataManager.getInstance().dataContextFromFocusAsync.onSuccess {
          CommonDataKeys.PROJECT.getData(it)?.messageBus?.syncPublisher(ProfileChangeAdapter.TOPIC)?.profileActivated(oldScheme, newScheme)
        }
      }
    }, settingsCategory = SettingsCategory.CODE)

  protected val profilesAreInitialized: Unit by lazy {
    val app = ApplicationManager.getApplication()
    if (!(app.isUnitTestMode || app.isHeadlessEnvironment)) {
      BUNDLED_EP_NAME.processWithPluginDescriptor { ep, pluginDescriptor ->
        schemeManager.loadBundledScheme(resourceName = ep.path!! + ".xml", requestor = null, pluginDescriptor = pluginDescriptor)
      }
    }
    schemeManager.loadSchemes()

    if (schemeManager.isEmpty) {
      schemeManager.addScheme(InspectionProfileImpl(InspectionProfile.DEFAULT_PROFILE_NAME, InspectionToolRegistrar.getInstance(), this))
    }
  }

  @Volatile
  @Suppress("PropertyName")
  protected var LOAD_PROFILES: Boolean = !ApplicationManager.getApplication().isUnitTestMode

  override fun getProfiles(): Collection<InspectionProfileImpl> {
    initProfiles()
    return Collections.unmodifiableList(schemeManager.allSchemes)
  }

  fun initProfiles() {
    if (LOAD_PROFILES) {
      profilesAreInitialized
    }
  }

  @Throws(JDOMException::class, IOException::class)
  open fun loadProfile(path: String): InspectionProfileImpl? {
    val file = Path.of(path)
    return if (Files.isRegularFile(file)) InspectionProfileLoadUtil.load(file, InspectionToolRegistrar.getInstance(), this)
           else getProfile(path, false)
  }

  override fun setRootProfile(profileName: String?) {
    schemeManager.setCurrentSchemeName(profileName, true)
  }

  override fun getProfile(name: String, returnRootProfileIfNamedIsAbsent: Boolean): InspectionProfileImpl? =
    schemeManager.findSchemeByName(name)
    ?: if (returnRootProfileIfNamedIsAbsent) currentProfile else null  // a profile was deleted

  override fun getCurrentProfile(): InspectionProfileImpl {
    initProfiles()

    val current = schemeManager.activeScheme
    if (current != null) return current

    val result = schemeManager.findSchemeByName(InspectionProfile.DEFAULT_PROFILE_NAME)
    if (result != null) return result

    // use default as base, not random custom profile
    val profile = InspectionProfileImpl(InspectionProfile.DEFAULT_PROFILE_NAME)
    addProfile(profile)
    return profile
  }

  override fun fireProfileChanged(profile: InspectionProfileImpl) { }

  companion object {
    private val BUNDLED_EP_NAME = ExtensionPointName<BundledSchemeEP>("com.intellij.bundledInspectionProfile")

    @JvmStatic
    fun getInstanceBase(): ApplicationInspectionProfileManagerBase =
      service<InspectionProfileManager>() as ApplicationInspectionProfileManagerBase
  }
}
