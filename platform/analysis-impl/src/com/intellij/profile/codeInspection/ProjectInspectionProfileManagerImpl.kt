/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.profile.codeInspection

import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.LazySchemeProcessor
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.profile.Profile
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters
import com.intellij.util.xmlb.XmlSerializer
import gnu.trove.THashSet
import org.jdom.Element
import java.util.function.Function

const val PROFILE = "profile"
const val SCOPES = "scopes"

private const val VERSION = "1.0"
private const val SCOPE = "scope"
private const val NAME = "name"
private const val PROJECT_DEFAULT_PROFILE_NAME = "Project Default"

@State(name = "InspectionProjectProfileManager", storages = arrayOf(Storage(value = "inspectionProfiles/profiles_settings.xml", exclusive = true)))
class ProjectInspectionProfileManagerImpl(val project: Project,
                                          private val applicationProfileManager: InspectionProfileManager,
                                          private val scopeManager: DependencyValidationManager,
                                          private val localScopesHolder: NamedScopeManager,
                                          schemeManagerFactory: SchemeManagerFactory) : BaseInspectionProfileManager(project.messageBus), PersistentStateComponent<Element>, InspectionProjectProfileManager {
  companion object {
    @JvmStatic
    fun getInstanceImpl(project: Project): ProjectInspectionProfileManagerImpl {
      return InspectionProjectProfileManager.getInstance(project) as ProjectInspectionProfileManagerImpl
    }
  }

  private var scopeListener: NamedScopesHolder.ScopeListener? = null

  private var state = State()

  private val skipDefaultsSerializationFilter = object : SkipDefaultValuesSerializationFilters(State()) {
    override fun accepts(accessor: Accessor, bean: Any, beanValue: Any?): Boolean {
      if (beanValue == null && accessor.name == "projectProfile") {
        return false
      }
      return super.accepts(accessor, bean, beanValue)
    }
  }

  override val schemeManager: SchemeManager<InspectionProfile>

  private data class State(@field:com.intellij.util.xmlb.annotations.OptionTag("PROJECT_PROFILE") var projectProfile: String? = PROJECT_DEFAULT_PROFILE_NAME,
                           @field:com.intellij.util.xmlb.annotations.OptionTag("USE_PROJECT_PROFILE") var useProjectProfile: Boolean = true)

  init {
    schemeManager = schemeManagerFactory.create("inspectionProfiles", object : LazySchemeProcessor<InspectionProfile, InspectionProfileImpl>() {
      override fun createScheme(dataHolder: SchemeDataHolder, name: String, attributeProvider: Function<String, String?>, duringLoad: Boolean): InspectionProfileImpl {
        val profile = InspectionProfileImpl(name, InspectionToolRegistrar.getInstance(), this@ProjectInspectionProfileManagerImpl, InspectionProfileImpl.getDefaultProfile(), dataHolder)
        profile.isProjectLevel = true
        return profile
      }

      override fun isSchemeFile(name: CharSequence) = !StringUtil.equals(name, "profiles_settings.xml")

      override fun onSchemeDeleted(scheme: InspectionProfileImpl) {
        schemeRemoved(scheme)
      }

      override fun onSchemeAdded(scheme: InspectionProfileImpl) {
        fireProfileChanged(scheme)
      }
    }, isUseOldFileNameSanitize = true)

    project.messageBus.connect().subscribe(ProjectManager.TOPIC, object: ProjectManagerListener {
      override fun projectClosed(project: Project) {
        val cleanupInspectionProfilesRunnable = {
          cleanupSchemes(project)
          (InspectionProfileManager.getInstance() as BaseInspectionProfileManager).cleanupSchemes(project)
          fireProfilesShutdown()
        }

        val app = ApplicationManager.getApplication()
        if (app.isUnitTestMode || app.isHeadlessEnvironment) {
          cleanupInspectionProfilesRunnable.invoke()
        }
        else {
          app.executeOnPooledThread(cleanupInspectionProfilesRunnable)
        }
      }
    })
  }

  fun isCurrentProfileInitialized() = currentProfile.wasInitialized()

  @Synchronized override fun updateProfile(profile: Profile) {
    super.updateProfile(profile)
    initInspectionTools(profile)
  }

  override fun schemeRemoved(scheme: InspectionProfile) {
    scheme.cleanup(project)
  }

  @Suppress("unused")
  private class ProjectInspectionProfileStartUpActivity : StartupActivity {
    override fun runActivity(project: Project) {
      getInstanceImpl(project).apply {
        schemeManager.loadSchemes()

        val inspectionProfile = currentProfile
        val app = ApplicationManager.getApplication()
        val initInspectionProfilesRunnable = {
          initInspectionTools(inspectionProfile)
          fireProfilesInitialized()
        }
        if (app.isUnitTestMode || app.isHeadlessEnvironment) {
          initInspectionProfilesRunnable.invoke()
          if (app.isDispatchThread) {
            //do not restart daemon in the middle of the test
            //noinspection TestOnlyProblems
            UIUtil.dispatchAllInvocationEvents()
          }
        }
        else {
          app.executeOnPooledThread(initInspectionProfilesRunnable)
        }
        scopeListener = NamedScopesHolder.ScopeListener {
          for (profile in schemeManager.allSchemes) {
            profile.scopesChanged()
          }
        }

        scopeManager.addScopeListener(scopeListener!!)
        localScopesHolder.addScopeListener(scopeListener!!)
        Disposer.register(project, Disposable {
          scopeManager.removeScopeListener(scopeListener!!)
          localScopesHolder.removeScopeListener(scopeListener!!)
        })
      }
    }
  }

  fun initInspectionTools(profile: Profile) {
    if (profile is InspectionProfileImpl) {
      profile.initInspectionTools(project)
    }
  }

  @Synchronized override fun loadState(state: Element) {
    try {
      severityRegistrar.readExternal(state)
    }
    catch (e: Throwable) {
      LOG.error(e)
    }

    val profileKeys = THashSet<String>()
    profileKeys.addAll(schemeManager.allSchemeNames)
    val newState = State()
    XmlSerializer.deserializeInto(newState, state)
    this.state = newState
    if (state.getChild("version")?.getAttributeValue("value") != VERSION) {
      for (o in state.getChildren("option")) {
        if (o.getAttributeValue("name") == "USE_PROJECT_LEVEL_SETTINGS") {
          if (o.getAttributeValue("value").toBoolean()) {
            if (newState.projectProfile != null) {
              currentProfile.convert(state, project)
            }
          }
          break
        }
      }
    }
  }

  @Synchronized override fun getState(): Element? {
    val result = Element("state")
    XmlSerializer.serializeInto(this.state, result, skipDefaultsSerializationFilter)
    if (!result.children.isEmpty()) {
      result.addContent(Element("version").setAttribute("value", VERSION))
    }

    severityRegistrar.writeExternal(result)
    return result
  }

  override fun getScopesManager() = scopeManager

  @Synchronized override fun getProfiles(): Collection<Profile> {
    currentProfile
    return schemeManager.allSchemes
  }

  @Synchronized override fun getAvailableProfileNames(): Array<String> = schemeManager.allSchemeNames.toTypedArray()

  val projectProfile: String?
    get() = state.projectProfile

  @Synchronized override fun setRootProfile(newProfile: String?) {
    if (newProfile == state.projectProfile) {
      return
    }

    val oldProfile = state.projectProfile
    state.projectProfile = newProfile
    state.useProjectProfile = newProfile != null
    oldProfile?.let {
      for (adapter in profileListeners) {
        adapter.profileActivated(getProfile(oldProfile), newProfile?.let { getProfile(it) })
      }
    }
  }

  @Synchronized override fun getCurrentProfile(): InspectionProfileImpl {
    if (!state.useProjectProfile) {
      return applicationProfileManager.currentProfile as InspectionProfileImpl
    }

    val currentName = state.projectProfile
    if (currentName == null || schemeManager.isEmpty) {
      state.projectProfile = PROJECT_DEFAULT_PROFILE_NAME
      val projectProfile = InspectionProfileImpl(PROJECT_DEFAULT_PROFILE_NAME, InspectionToolRegistrar.getInstance(), this, InspectionProfileImpl.getDefaultProfile(), null)
      projectProfile.copyFrom(applicationProfileManager.currentProfile)
      projectProfile.isProjectLevel = true
      projectProfile.setName(PROJECT_DEFAULT_PROFILE_NAME)
      schemeManager.addScheme(projectProfile)
      return projectProfile
    }

    var profile = schemeManager.findSchemeByName(currentName)
    if (profile == null) {
      profile = schemeManager.allSchemes.get(0)!!
      state.projectProfile = profile.name
    }
    return profile as InspectionProfileImpl
  }

  private fun fireProfilesInitialized() {
    for (listener in profileListeners) {
      listener.profilesInitialized()
    }
  }

  private fun fireProfilesShutdown() {
    for (profileChangeAdapter in profileListeners) {
      profileChangeAdapter.profilesShutdown()
    }
  }

  @Synchronized override fun getProfile(name: String, returnRootProfileIfNamedIsAbsent: Boolean): Profile? {
    val profile = schemeManager.findSchemeByName(name)
    return profile ?: applicationProfileManager.getProfile(name, returnRootProfileIfNamedIsAbsent)
  }
}