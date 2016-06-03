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

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionProfileWrapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.MainConfigurationStateSplitter
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.profile.Profile
import com.intellij.profile.ProfileChangeAdapter
import com.intellij.profile.ProfileEx
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.OptionTag
import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jdom.Element
import java.util.*
import java.util.concurrent.ConcurrentHashMap

const val PROFILE = "profile"
const val SCOPES = "scopes"

private val LOG = Logger.getInstance(InspectionProjectProfileManagerImpl::class.java)
private const val VERSION = "1.0"
private const val SCOPE = "scope"
private const val NAME = "name"
private const val PROJECT_DEFAULT_PROFILE_NAME = "Project Default"

@State(name = "InspectionProjectProfileManager", storages = arrayOf(Storage(value = "inspectionProfiles", stateSplitter = ProfileStateSplitter::class)))
class InspectionProjectProfileManagerImpl(private val myProject: Project,
                                          private val applicationProfileManager: InspectionProfileManager,
                                          private val myHolder: DependencyValidationManager,
                                          private val myLocalScopesHolder: NamedScopeManager) : PersistentStateComponent<Element>, InspectionProjectProfileManager {
  companion object {
    @JvmStatic
    fun getInstanceImpl(project: Project): InspectionProjectProfileManagerImpl {
      return InspectionProjectProfileManager.getInstance(project) as InspectionProjectProfileManagerImpl
    }
  }

  private val nameToProfile = ConcurrentHashMap<String, InspectionProfileWrapper>()
  private val appNameToProfile = ConcurrentHashMap<String, InspectionProfileWrapper>()

  private val severityRegistrar = SeverityRegistrar(myProject.messageBus)
  private var scopeListener: NamedScopesHolder.ScopeListener? = null

  private val profiles = THashMap<String, InspectionProfile>()
  private val profileListeners = ContainerUtil.createLockFreeCopyOnWriteList<ProfileChangeAdapter>()

  private var projectProfile: String? = null

  @OptionTag("USE_PROJECT_PROFILE")
  private var useProjectProfile = true

  init {
    project.messageBus.connect().subscribe(ProjectManager.TOPIC, object: ProjectManagerListener {
      override fun projectClosed(project: Project?) {
        val cleanupInspectionProfilesRunnable = {
          for (wrapper in nameToProfile.values) {
            wrapper.cleanup(myProject)
          }
          for (wrapper in appNameToProfile.values) {
            wrapper.cleanup(myProject)
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
  }

  override fun getProject() = myProject

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
    for (profileChangeAdapter in profileListeners) {
      profileChangeAdapter.profileChanged(profile)
    }

    initProfileWrapper(profile)
  }

  @Synchronized override fun deleteProfile(name: String) {
    profiles.remove(name)
    nameToProfile.remove(name)?.cleanup(myProject)
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
        myHolder.addScopeListener(scopeListener!!)
        myLocalScopesHolder.addScopeListener(scopeListener!!)
        Disposer.register(myProject, Disposable {
          myHolder.removeScopeListener(scopeListener!!)
          myLocalScopesHolder.removeScopeListener(scopeListener!!)
        })
      }
    }
  }

  override fun initProfileWrapper(profile: Profile) {
    val wrapper = InspectionProfileWrapper(profile as InspectionProfile)
    if (profile is InspectionProfileImpl) {
      profile.initInspectionTools(myProject)
    }
    if (profile.getProfileManager() === this) {
      nameToProfile.put(profile.name, wrapper)
    }
    else {
      appNameToProfile.put(profile.name, wrapper)
    }
  }

  override fun getSeverityRegistrar() = severityRegistrar

  override fun getOwnSeverityRegistrar() = severityRegistrar

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
    XmlSerializer.deserializeInto(this, state)
    for (o in state.getChildren(PROFILE)) {
      val profile = applicationProfileManager.createProfile()
      profile.profileManager = this
      profile.readExternal(o)
      profile.isProjectLevel = true
      if (profileKeys.contains(profile.name)) {
        updateProfile(profile)
      }
      else {
        profiles.put(profile.name, profile as InspectionProfile?)
      }
    }
    if (state.getChild("version") == null || !Comparing.strEqual(state.getChild("version").getAttributeValue("value"), VERSION)) {
      var toConvert = true
      for (o in state.getChildren("option")) {
        if (Comparing.strEqual(o.getAttributeValue("name"), "USE_PROJECT_LEVEL_SETTINGS")) {
          toConvert = java.lang.Boolean.parseBoolean(o.getAttributeValue("value"))
          break
        }
      }
      if (toConvert) {
        convert(state)
      }
    }
  }

  @Synchronized override fun getState(): Element? {
    val state = Element("settings")

    val sortedProfiles = profiles.keys.toTypedArray<String>()
    Arrays.sort(sortedProfiles)
    for (profile in sortedProfiles) {
      val projectProfile = profiles[profile]
      if (projectProfile != null) {
        val profileElement = ProfileEx.serializeProfile(projectProfile)
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
          state.addContent(profileElement)
        }
      }
    }

    if (!state.children.isEmpty() || isCustomProfileUsed) {
      XmlSerializer.serializeInto(this, state)
      state.addContent(Element("version").setAttribute("value", VERSION))
    }

    severityRegistrar.writeExternal(state)
    return state
  }

  private val isCustomProfileUsed: Boolean
    get() = projectProfile != null && !Comparing.strEqual(projectProfile, PROJECT_DEFAULT_PROFILE_NAME)

  override fun getProfile(name: String) = getProfile(name, true)

  override fun getScopesManager() = myHolder

  @Synchronized override fun getProfiles(): Collection<Profile> {
    inspectionProfile
    return profiles.values
  }

  @Synchronized override fun getAvailableProfileNames() = profiles.keys.toTypedArray()

  @OptionTag("PROJECT_PROFILE")
  @Synchronized override fun getProjectProfile() = projectProfile

  @Synchronized override fun setProjectProfile(newProfile: String?) {
    if (Comparing.strEqual(newProfile, projectProfile)) {
      return
    }

    val oldProfile = projectProfile
    projectProfile = newProfile
    useProjectProfile = newProfile != null
    if (oldProfile != null) {
      for (adapter in profileListeners) {
        adapter.profileActivated(getProfile(oldProfile), if (newProfile != null) getProfile(newProfile) else null)
      }
    }
  }

  @Synchronized override fun getInspectionProfile(): InspectionProfile {
    if (!useProjectProfile) {
      return applicationProfileManager.rootProfile as InspectionProfile
    }
    if (projectProfile == null || profiles.isEmpty) {
      projectProfile = PROJECT_DEFAULT_PROFILE_NAME
      val projectProfile = applicationProfileManager.createProfile()
      projectProfile.copyFrom(applicationProfileManager.rootProfile)
      projectProfile.isProjectLevel = true
      projectProfile.setName(PROJECT_DEFAULT_PROFILE_NAME)
      profiles.put(PROJECT_DEFAULT_PROFILE_NAME, projectProfile as InspectionProfile?)
    }
    else if (!profiles.containsKey(projectProfile)) {
      projectProfile = profiles.keys.iterator().next()
    }
    val profile = profiles.get(projectProfile)!!
    if (profile.isProjectLevel) {
      profile.profileManager = this
    }
    return profile
  }

  override fun addProfilesListener(listener: ProfileChangeAdapter, parent: Disposable) {
    profileListeners.add(listener)
    Disposer.register(parent, Disposable { profileListeners.remove(listener) })
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

  @Synchronized override fun getProfile(name: String, returnRootProfileIfNamedIsAbsent: Boolean): Profile {
    val profile = profiles.get(name)
    return profile ?: applicationProfileManager.getProfile(name, returnRootProfileIfNamedIsAbsent)
  }

  fun convert(element: Element) {
    if (projectProfile != null) {
      (inspectionProfile as ProfileEx).convert(element, project)
    }
  }
}

private class ProfileStateSplitter : MainConfigurationStateSplitter() {
  override fun getComponentStateFileName(): String = "profiles_settings"

  override fun getSubStateTagName() = PROFILE
}