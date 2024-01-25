// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.configurationStore.*
import com.intellij.diagnostic.runActivity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.profile.ProfileChangeAdapter
import com.intellij.project.isDirectoryBased
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.util.xmlb.annotations.OptionTag
import kotlinx.coroutines.launch
import org.jdom.Element
import org.jetbrains.annotations.TestOnly

const val PROJECT_DEFAULT_PROFILE_NAME: String = "Project Default"
const val PROFILE_DIR: String = "inspectionProfiles"
const val PROFILES_SETTINGS: String = "profiles_settings.xml"

private const val VERSION = "1.0"

private val defaultSchemeDigest = hashElement(JDOMUtil.load("""<component name="InspectionProjectProfileManager">
  <profile version="1.0">
    <option name="myName" value="Project Default" />
  </profile>
</component>"""))

private val LOG = logger<ProjectInspectionProfileManager>()

@State(name = "InspectionProjectProfileManager", storages = [(Storage(value = "$PROFILE_DIR/profiles_settings.xml", exclusive = true))])
open class ProjectInspectionProfileManager(final override val project: Project) : BaseInspectionProfileManager(project.messageBus),
                                                                                  PersistentStateComponentWithModificationTracker<Element>,
                                                                                  ProjectBasedInspectionProfileManager,
                                                                                  Disposable {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectInspectionProfileManager =
      InspectionProjectProfileManager.getInstance(project) as ProjectInspectionProfileManager
  }

  private var state = ProjectInspectionProfileManagerState()

  private val schemeManagerIprProvider = if (project.isDirectoryBased) null else SchemeManagerIprProvider("profile")

  override val schemeManager: SchemeManager<InspectionProfileImpl> =
    SchemeManagerFactory.getInstance(project).create(PROFILE_DIR, object : InspectionProfileProcessor() {
      override fun createScheme(dataHolder: SchemeDataHolder<InspectionProfileImpl>,
                                name: String,
                                attributeProvider: (String) -> String?,
                                isBundled: Boolean): InspectionProfileImpl {
        val profile = InspectionProfileImpl(name, InspectionToolRegistrar.getInstance(), this@ProjectInspectionProfileManager, dataHolder)
        profile.isProjectLevel = true
        return profile
      }

      override fun isSchemeFile(name: CharSequence): Boolean = name != PROFILES_SETTINGS

      override fun isSchemeDefault(scheme: InspectionProfileImpl, digest: Long): Boolean =
        scheme.name == PROJECT_DEFAULT_PROFILE_NAME && digest == defaultSchemeDigest

      override fun onSchemeDeleted(scheme: InspectionProfileImpl) {
        schemeRemoved(scheme)
      }

      override fun onSchemeAdded(scheme: InspectionProfileImpl) {
        if (scheme.wasInitialized()) {
          fireProfileChanged(scheme)
        }
      }

      override fun onCurrentSchemeSwitched(oldScheme: InspectionProfileImpl?,
                                           newScheme: InspectionProfileImpl?,
                                           processChangeSynchronously: Boolean) {
        project.messageBus.syncPublisher(ProfileChangeAdapter.TOPIC).profileActivated(oldScheme, newScheme)
      }
    }, schemeNameToFileName = OLD_NAME_CONVERTER, streamProvider = schemeManagerIprProvider)

  override fun initializeComponent() {
    val app = ApplicationManager.getApplication()
    if (!project.isDirectoryBased || app.isUnitTestMode) {
      return
    }

    runActivity("project inspection profile loading") {
      schemeManager.loadSchemes()
      currentProfile.initInspectionTools(project)
    }

    StartupManager.getInstance(project).runAfterOpened {
      project.messageBus.syncPublisher(ProfileChangeAdapter.TOPIC).profilesInitialized()

      val projectScopeListener = NamedScopesHolder.ScopeListener {
        for (profile in schemeManager.allSchemes) {
          profile.scopesChanged()
        }
      }

      scopesManager.addScopeListener(projectScopeListener, project)
      NamedScopeManager.getInstance(project).addScopeListener(projectScopeListener, project)
    }
  }

  override fun dispose() {
    val cleanupInspectionProfilesRunnable = {
      cleanupSchemes(project)
      (serviceIfCreated<InspectionProfileManager>() as BaseInspectionProfileManager?)?.cleanupSchemes(project)
    }

    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      cleanupInspectionProfilesRunnable.invoke()
    }
    else {
      (app as ComponentManagerEx).getCoroutineScope().launch { cleanupInspectionProfilesRunnable() }
    }
  }

  override fun getStateModificationCount(): Long =
    state.modificationCount + severityRegistrar.modificationCount + (schemeManagerIprProvider?.modificationCount ?: 0)

  @TestOnly
  fun forceLoadSchemes() {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode)
    schemeManager.loadSchemes()
  }

  fun isCurrentProfileInitialized(): Boolean = currentProfile.wasInitialized()

  override fun schemeRemoved(scheme: InspectionProfileImpl) {
    scheme.cleanup(project)
  }

  @Synchronized
  override fun getState(): Element? {
    val result = Element("settings")

    schemeManagerIprProvider?.writeState(result)

    serializeObjectInto(state, result)
    if (result.children.isNotEmpty()) {
      result.addContent(Element("version").setAttribute("value", VERSION))
    }

    severityRegistrar.writeExternal(result)

    return wrapState(result, project)
  }

  @Synchronized
  override fun loadState(state: Element) {
    val data = unwrapState(state, project, schemeManagerIprProvider, schemeManager)

    val newState = ProjectInspectionProfileManagerState()

    data?.let {
      try {
        severityRegistrar.readExternal(it)
      }
      catch (e: Throwable) {
        LOG.error(e)
      }

      it.deserializeInto(newState)
    }

    this.state = newState

    if (data != null && data.getChild("version")?.getAttributeValue("value") != VERSION) {
      for (o in data.getChildren("option")) {
        if (o.getAttributeValue("name") == "USE_PROJECT_LEVEL_SETTINGS") {
          if (o.getAttributeBooleanValue("value") && newState.projectProfile != null) {
            currentProfile.convert(data, project)
          }
          break
        }
      }
    }
  }

  override fun getScopesManager(): DependencyValidationManager = DependencyValidationManager.getInstance(project)

  @Synchronized
  override fun getProfiles(): Collection<InspectionProfileImpl> {
    currentProfile
    return schemeManager.allSchemes
  }

  val projectProfile: String?
    get() = state.projectProfile

  @Synchronized
  override fun setRootProfile(name: String?) {
    state.useProjectProfile = name != null
    if (name != null) {
      state.projectProfile = name
    }
    schemeManager.setCurrentSchemeName(name, true)
  }

  @Synchronized
  fun useApplicationProfile(name: String) {
    state.useProjectProfile = false
    // yes, we reuse the same field - useProjectProfile field will be used to distinguish - is an it app or project level
    // to avoid data format change
    state.projectProfile = name
  }

  @Synchronized
  @TestOnly
  fun setCurrentProfile(profile: InspectionProfileImpl?) {
    schemeManager.setCurrent(profile)
    state.useProjectProfile = profile != null
    if (profile != null) {
      state.projectProfile = profile.name
    }
  }

  @Synchronized
  override fun getCurrentProfile(): InspectionProfileImpl {
    if (!state.useProjectProfile) {
      val applicationProfileManager = InspectionProfileManager.getInstance()
      return (state.projectProfile?.let {
        applicationProfileManager.getProfile(it, false)
      } ?: applicationProfileManager.currentProfile)
    }

    var currentScheme = state.projectProfile?.let { schemeManager.findSchemeByName(it) }
    if (currentScheme == null) {
      currentScheme = schemeManager.allSchemes.firstOrNull()
      if (currentScheme == null) {
        currentScheme = InspectionProfileImpl(PROJECT_DEFAULT_PROFILE_NAME, InspectionToolRegistrar.getInstance(), this)
        currentScheme.copyFrom(InspectionProfileManager.getInstance().currentProfile)
        currentScheme.isProjectLevel = true
        currentScheme.name = PROJECT_DEFAULT_PROFILE_NAME
        schemeManager.addScheme(currentScheme)
      }
      schemeManager.setCurrent(currentScheme, false)
    }
    return currentScheme
  }

  @Synchronized
  override fun getProfile(name: String, returnRootProfileIfNamedIsAbsent: Boolean): InspectionProfileImpl? =
    schemeManager.findSchemeByName(name) ?: InspectionProfileManager.getInstance().getProfile(name, returnRootProfileIfNamedIsAbsent)

  fun fireProfileChanged() {
    fireProfileChanged(currentProfile)
  }

  override fun fireProfileChanged(profile: InspectionProfileImpl) {
    profile.profileChanged()
    project.messageBus.syncPublisher(ProfileChangeAdapter.TOPIC).profileChanged(profile)
  }
}

private class ProjectInspectionProfileManagerState : BaseState() {
  @get:OptionTag("PROJECT_PROFILE")
  var projectProfile by string(PROJECT_DEFAULT_PROFILE_NAME)

  @get:OptionTag("USE_PROJECT_PROFILE")
  var useProjectProfile by property(true)
}
