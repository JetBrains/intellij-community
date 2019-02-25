// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.configurationStore.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.profile.ProfileChangeAdapter
import com.intellij.project.isDirectoryBased
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.util.getAttributeBooleanValue
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters
import com.intellij.util.xmlb.annotations.OptionTag
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.*
import java.util.*
import java.util.function.Function

private const val VERSION = "1.0"
private const val PROJECT_DEFAULT_PROFILE_NAME = "Project Default"

private val defaultSchemeDigest = JDOMUtil.load("""<component name="InspectionProjectProfileManager">
  <profile version="1.0">
    <option name="myName" value="Project Default" />
  </profile>
</component>""").digest()

@State(name = "InspectionProjectProfileManager", storages = [(Storage(value = "inspectionProfiles/profiles_settings.xml", exclusive = true))])
class ProjectInspectionProfileManager(val project: Project) : BaseInspectionProfileManager(project.messageBus), PersistentStateComponentWithModificationTracker<Element> {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectInspectionProfileManager {
      return project.getComponent(ProjectInspectionProfileManager::class.java)
    }
  }

  private var state = State()

  private val initialLoadSchemesFuture: Promise<Any?>

  private val skipDefaultsSerializationFilter = object : SkipDefaultValuesSerializationFilters(State()) {
    override fun accepts(accessor: Accessor, bean: Any, beanValue: Any?): Boolean {
      if (beanValue == null && accessor.name == "projectProfile") {
        return false
      }
      return super.accepts(accessor, bean, beanValue)
    }
  }

  private val schemeManagerIprProvider = if (project.isDirectoryBased) null else SchemeManagerIprProvider("profile")

  override val schemeManager: SchemeManager<InspectionProfileImpl> = SchemeManagerFactory.getInstance(project).create("inspectionProfiles", object : InspectionProfileProcessor() {
    override fun createScheme(dataHolder: SchemeDataHolder<InspectionProfileImpl>,
                              name: String,
                              attributeProvider: Function<in String, String?>,
                              isBundled: Boolean): InspectionProfileImpl {
      val profile = InspectionProfileImpl(name, InspectionToolRegistrar.getInstance(), this@ProjectInspectionProfileManager, dataHolder)
      profile.isProjectLevel = true
      return profile
    }

    override fun isSchemeFile(name: CharSequence) = !StringUtil.equals(name, "profiles_settings.xml")

    override fun isSchemeDefault(scheme: InspectionProfileImpl, digest: ByteArray): Boolean {
      return scheme.name == PROJECT_DEFAULT_PROFILE_NAME && Arrays.equals(digest, defaultSchemeDigest)
    }

    override fun onSchemeDeleted(scheme: InspectionProfileImpl) {
      schemeRemoved(scheme)
    }

    override fun onSchemeAdded(scheme: InspectionProfileImpl) {
      if (scheme.wasInitialized()) {
        fireProfileChanged(scheme)
      }
    }

    override fun onCurrentSchemeSwitched(oldScheme: InspectionProfileImpl?, newScheme: InspectionProfileImpl?) {
      project.messageBus.syncPublisher(ProfileChangeAdapter.TOPIC).profileActivated(oldScheme, newScheme)
    }
  }, schemeNameToFileName = OLD_NAME_CONVERTER, streamProvider = schemeManagerIprProvider)

  private class State : BaseState() {
    @get:OptionTag("PROJECT_PROFILE")
    var projectProfile by string(PROJECT_DEFAULT_PROFILE_NAME)

    @get:OptionTag("USE_PROJECT_PROFILE")
    var useProjectProfile by property(true)
  }

  init {
    val app = ApplicationManager.getApplication()
    if (!project.isDirectoryBased || app.isUnitTestMode) {
      initialLoadSchemesFuture = resolvedPromise()
    }
    else {
      initialLoadSchemesFuture = runAsync { schemeManager.loadSchemes() }
    }

    project.messageBus.connect().subscribe(ProjectManager.TOPIC, object: ProjectManagerListener {
      override fun projectClosed(project: Project) {
        val cleanupInspectionProfilesRunnable = {
          cleanupSchemes(project)
          (InspectionProfileManager.getInstance() as BaseInspectionProfileManager).cleanupSchemes(project)
          this@ProjectInspectionProfileManager.project.messageBus.syncPublisher(ProfileChangeAdapter.TOPIC).profilesShutdown()
          Unit
        }

        if (app.isUnitTestMode || app.isHeadlessEnvironment) {
          cleanupInspectionProfilesRunnable.invoke()
        }
        else {
          app.executeOnPooledThread(cleanupInspectionProfilesRunnable)
        }
      }
    })
  }

  override fun getStateModificationCount() = state.modificationCount + severityRegistrar.modificationCount  + (schemeManagerIprProvider?.modificationCount ?: 0)

  @TestOnly
  fun forceLoadSchemes() {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode)
    schemeManager.loadSchemes()
  }

  fun isCurrentProfileInitialized() = !initialLoadSchemesFuture.isPending && currentProfile.wasInitialized()

  override fun schemeRemoved(scheme: InspectionProfileImpl) {
    scheme.cleanup(project)
  }

  @Suppress("unused")
  private class ProjectInspectionProfileStartUpActivity : StartupActivity, DumbAware {
    override fun runActivity(project: Project) {
      val profileManager = getInstance(project)
      profileManager.initialLoadSchemesFuture
        .onSuccess {
          if (!project.isDisposed) {
            profileManager.currentProfile.initInspectionTools(project)
            project.messageBus.syncPublisher(ProfileChangeAdapter.TOPIC).profilesInitialized()
          }
        }

      profileManager.initialLoadSchemesFuture.onProcessed {
        val scopeListener = NamedScopesHolder.ScopeListener {
          for (profile in profileManager.schemeManager.allSchemes) {
            profile.scopesChanged()
          }
        }

        profileManager.scopesManager.addScopeListener(scopeListener, project)
        NamedScopeManager.getInstance(project).addScopeListener(scopeListener, project)
      }

      Disposer.register(project, Disposable {
        (profileManager.initialLoadSchemesFuture as? AsyncPromise<*>)?.cancel()
      })
    }
  }

  @Synchronized
  override fun getState(): Element? {
    if (initialLoadSchemesFuture.isPending) {
      return null
    }

    val result = Element("settings")

    schemeManagerIprProvider?.writeState(result)

    serializeObjectInto(state, result, skipDefaultsSerializationFilter)
    if (!result.children.isEmpty()) {
      result.addContent(Element("version").setAttribute("value", VERSION))
    }

    severityRegistrar.writeExternal(result)

    return wrapState(result, project)
  }

  @Synchronized
  override fun loadState(state: Element) {
    val data = unwrapState(state, project, schemeManagerIprProvider, schemeManager)

    val newState = State()

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

  override fun getScopesManager() = DependencyValidationManager.getInstance(project)

  @Synchronized
  override fun getProfiles(): Collection<InspectionProfileImpl> {
    currentProfile
    return schemeManager.allSchemes
  }

  val projectProfile: String?
    get() = state.projectProfile

  @Synchronized
  override fun setRootProfile(name: String?) {
    if (name != state.projectProfile) {
      state.useProjectProfile = name != null
      if (name != null) {
        state.projectProfile = name
      }
    }
  }

  @Synchronized
  fun useApplicationProfile(name: String) {
    state.useProjectProfile = false
    // yes, we reuse the same field - useProjectProfile field will be used to distinguish - is it app or project level
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
  override fun getProfile(name: String, returnRootProfileIfNamedIsAbsent: Boolean): InspectionProfileImpl? {
    val profile = schemeManager.findSchemeByName(name)
    return profile ?: InspectionProfileManager.getInstance().getProfile(name, returnRootProfileIfNamedIsAbsent)
  }

  fun fireProfileChanged() {
    fireProfileChanged(currentProfile)
  }

  override fun fireProfileChanged(profile: InspectionProfileImpl) {
    profile.profileChanged()
    project.messageBus.syncPublisher(ProfileChangeAdapter.TOPIC).profileChanged(profile)
  }
}