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
import com.intellij.codeInspection.ex.InspectionProfileWrapper
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
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.profile.Profile
import com.intellij.profile.ProfileEx
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.OptionTag
import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jdom.Element
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

const val PROFILE = "profile"
const val SCOPES = "scopes"

private const val VERSION = "1.0"
private const val SCOPE = "scope"
private const val NAME = "name"
private const val PROJECT_DEFAULT_PROFILE_NAME = "Project Default"

@State(name = "InspectionProjectProfileManager", storages = arrayOf(Storage("inspectionProfiles/profiles_settings")))
class ProjectInspectionProfileManagerImpl(private val project: Project,
                                          private val applicationProfileManager: InspectionProfileManager,
                                          private val scopeManager: DependencyValidationManager,
                                          private val localScopesHolder: NamedScopeManager,
                                          private val schemeManagerFactory: SchemeManagerFactory) : BaseInspectionProfileManager(project.messageBus), PersistentStateComponent<Element>, InspectionProjectProfileManager {
  companion object {
    @JvmStatic
    fun getInstanceImpl(project: Project): ProjectInspectionProfileManagerImpl {
      return InspectionProjectProfileManager.getInstance(project) as ProjectInspectionProfileManagerImpl
    }
  }

  private val nameToProfile = ConcurrentHashMap<String, InspectionProfileWrapper>()
  private val appNameToProfile = ConcurrentHashMap<String, InspectionProfileWrapper>()

  private var scopeListener: NamedScopesHolder.ScopeListener? = null

  private val profiles = THashMap<String, InspectionProfile>()

  private var state = State()

  private val schemeManager: SchemeManager<InspectionProfile>

  init {
    project.messageBus.connect().subscribe(ProjectManager.TOPIC, object: ProjectManagerListener {
      override fun projectClosed(project: Project) {
        val cleanupInspectionProfilesRunnable = {
          for (wrapper in nameToProfile.values) {
            wrapper.cleanup(project)
          }
          for (wrapper in appNameToProfile.values) {
            wrapper.cleanup(project)
          }
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

    schemeManager = schemeManagerFactory.create("inspectionProfiles", object : LazySchemeProcessor<InspectionProfile, InspectionProfileImpl>() {
      override fun createScheme(dataHolder: SchemeDataHolder, name: String, attributeProvider: Function<String, String?>, duringLoad: Boolean): InspectionProfileImpl {
        val profile = InspectionProfileImpl(name, InspectionToolRegistrar.getInstance(), this@ProjectInspectionProfileManagerImpl, InspectionProfileImpl.getDefaultProfile(), dataHolder)
        profile.isProjectLevel = true
        return profile
      }
    })
  }

  private class State {
    @OptionTag("PROJECT_PROFILE")
    var projectProfile: String? = null

    @OptionTag("USE_PROJECT_PROFILE")
    var useProjectProfile = true
  }

  override fun getProject() = project

  override fun isProfileLoaded(): Boolean {
    val profile = inspectionProfile
    val name = profile.name
    return if (profile.profileManager === this) nameToProfile.containsKey(name) else appNameToProfile.containsKey(name)
  }

  val profileWrapper: InspectionProfileWrapper
    get() {
      val profile = inspectionProfile
      val profileName = profile.name
      val nameToProfile = if (profile.profileManager === this) nameToProfile else appNameToProfile
      val wrapper = nameToProfile[profileName]
      if (wrapper == null) {
        initProfileWrapper(profile)
        return nameToProfile.get(profileName)!!
      }
      return wrapper
    }

  @Synchronized override fun updateProfile(profile: Profile) {
    profiles.put(profile.name, profile as InspectionProfile)
    fireProfileChanged(profile)
    initProfileWrapper(profile)
  }

  @Synchronized override fun deleteProfile(name: String) {
    profiles.remove(name)
    nameToProfile.remove(name)?.cleanup(project)
  }

  @Suppress("unused")
  private class ProjectInspectionProfileStartUpActivity : StartupActivity {
    override fun runActivity(project: Project) {
      val profileManager = getInstanceImpl(project)
      val inspectionProfile = profileManager.inspectionProfile
      val app = ApplicationManager.getApplication()
      val initInspectionProfilesRunnable = {
        profileManager.initProfileWrapper(inspectionProfile)
        profileManager.fireProfilesInitialized()
      }
      if (app.isUnitTestMode || app.isHeadlessEnvironment) {
        initInspectionProfilesRunnable.invoke()
        //do not restart daemon in the middle of the test
        //noinspection TestOnlyProblems
        UIUtil.dispatchAllInvocationEvents()
      }
      else {
        app.executeOnPooledThread(initInspectionProfilesRunnable)
      }
      profileManager.scopeListener = NamedScopesHolder.ScopeListener {
        for (profile in profileManager.profiles.values) {
          profile.scopesChanged()
        }
      }
      profileManager.apply {
        scopeManager.addScopeListener(scopeListener!!)
        localScopesHolder.addScopeListener(scopeListener!!)
        Disposer.register(project, Disposable {
          scopeManager.removeScopeListener(scopeListener!!)
          localScopesHolder.removeScopeListener(scopeListener!!)
        })
      }
    }
  }

  fun initProfileWrapper(profile: Profile) {
    val wrapper = InspectionProfileWrapper(profile as InspectionProfile)
    if (profile is InspectionProfileImpl) {
      profile.initInspectionTools(project)
    }
    if (profile.getProfileManager() === this) {
      nameToProfile.put(profile.name, wrapper)
    }
    else {
      appNameToProfile.put(profile.name, wrapper)
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
    profileKeys.addAll(profiles.keys)
    profiles.clear()
    val newState = State()
    XmlSerializer.deserializeInto(newState, state)
    this.state = newState
//    for (o in state.getChildren(PROFILE)) {
//      val profile = applicationProfileManager.createProfile()
//      if (profileKeys.contains(profile.name)) {
//        updateProfile(profile)
//      }
//      else {
//        profiles.put(profile.name, profile as InspectionProfile?)
//      }
//    }
    if (state.getChild("version")?.getAttributeValue("value") != VERSION) {
      for (o in state.getChildren("option")) {
        if (o.getAttributeValue("name") == "USE_PROJECT_LEVEL_SETTINGS") {
          if (o.getAttributeValue("value").toBoolean()) {
            if (newState.projectProfile != null) {
              (inspectionProfile as ProfileEx).convert(state, project)
            }
          }
          break
        }
      }
    }
  }

  @Synchronized override fun getState(): Element? {
    val result = Element("settings")

    val sortedProfiles = profiles.keys.toTypedArray<String>()
    Arrays.sort(sortedProfiles)
    for (profile in sortedProfiles) {
      profiles.get(profile)?.let {
        val profileElement = ProfileEx.serializeProfile(it)
        var hasSmthToSave = sortedProfiles.size > 1 || isCustomProfileUsed
        if (!hasSmthToSave) {
          for (child in profileElement.children) {
            if (child.name != "option") {
              hasSmthToSave = true
              break
            }
          }
        }
        if (hasSmthToSave) {
          result.addContent(profileElement)
        }
      }
    }

    if (!result.children.isEmpty() || isCustomProfileUsed) {
      XmlSerializer.serializeInto(this.state, result)
      result.addContent(Element("version").setAttribute("value", VERSION))
    }

    severityRegistrar.writeExternal(result)
    return result
  }

  private val isCustomProfileUsed: Boolean
    get() = state.projectProfile != null && state.projectProfile != PROJECT_DEFAULT_PROFILE_NAME

  override fun getProfile(name: String) = getProfile(name, true)

  override fun getScopesManager() = scopeManager

  @Synchronized override fun getProfiles(): Collection<Profile> {
    inspectionProfile
    return profiles.values
  }

  @Synchronized override fun getAvailableProfileNames() = profiles.keys.toTypedArray()

  val projectProfile: String?
    get() = state.projectProfile

  @Synchronized fun setProjectProfile(newProfile: String?) {
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

  @Synchronized override fun getInspectionProfile(): InspectionProfile {
    if (!state.useProjectProfile) {
      return applicationProfileManager.rootProfile as InspectionProfile
    }
    if (state.projectProfile == null || profiles.isEmpty) {
      state.projectProfile = PROJECT_DEFAULT_PROFILE_NAME
      val projectProfile = InspectionProfileImpl(PROJECT_DEFAULT_PROFILE_NAME, InspectionToolRegistrar.getInstance(), this@ProjectInspectionProfileManagerImpl, InspectionProfileImpl.getDefaultProfile(), null)
      projectProfile.copyFrom(applicationProfileManager.rootProfile)
      projectProfile.isProjectLevel = true
      projectProfile.setName(PROJECT_DEFAULT_PROFILE_NAME)
      profiles.put(PROJECT_DEFAULT_PROFILE_NAME, projectProfile)
    }
    else if (!profiles.containsKey(state.projectProfile)) {
      state.projectProfile = profiles.keys.iterator().next()
    }
    val profile = profiles.get(state.projectProfile)!!
    if (profile.isProjectLevel) {
      profile.profileManager = this
    }
    return profile
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
    val profile = profiles.get(name)
    return profile ?: applicationProfileManager.getProfile(name, returnRootProfileIfNamedIsAbsent)
  }
}