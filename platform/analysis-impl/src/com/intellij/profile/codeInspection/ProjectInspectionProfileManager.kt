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
import com.intellij.configurationStore.digest
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.profile.Profile
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.util.loadElement
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.concurrency.runAsync
import java.util.*
import java.util.function.Function

const val PROFILE = "profile"

private const val VERSION = "1.0"
private const val SCOPE = "scope"
private const val NAME = "name"
private const val PROJECT_DEFAULT_PROFILE_NAME = "Project Default"

private val defaultSchemeDigest = loadElement("""<component name="InspectionProjectProfileManager">
  <profile version="1.0">
    <option name="myName" value="Project Default" />
  </profile>
</component>""").digest()

@State(name = "InspectionProjectProfileManager", storages = arrayOf(Storage(value = "inspectionProfiles/profiles_settings.xml", exclusive = true)))
class ProjectInspectionProfileManager(val project: Project,
                                      private val applicationProfileManager: InspectionProfileManager,
                                      private val scopeManager: DependencyValidationManager,
                                      private val localScopesHolder: NamedScopeManager,
                                      schemeManagerFactory: SchemeManagerFactory) : BaseInspectionProfileManager(project.messageBus), PersistentStateComponent<Element> {
  companion object {
    @JvmStatic
    fun getInstanceImpl(project: Project): ProjectInspectionProfileManager {
      return InspectionProjectProfileManager.getInstance(project) as ProjectInspectionProfileManager
    }
  }

  private var scopeListener: NamedScopesHolder.ScopeListener? = null

  private var state = State()

  private val initialLoadSchemesFuture: Promise<*>

  private val skipDefaultsSerializationFilter = object : SkipDefaultValuesSerializationFilters(State()) {
    override fun accepts(accessor: Accessor, bean: Any, beanValue: Any?): Boolean {
      if (beanValue == null && accessor.name == "projectProfile") {
        return false
      }
      return super.accepts(accessor, bean, beanValue)
    }
  }

  override val schemeManager: SchemeManager<InspectionProfileImpl>

  private data class State(@field:com.intellij.util.xmlb.annotations.OptionTag("PROJECT_PROFILE") var projectProfile: String? = PROJECT_DEFAULT_PROFILE_NAME,
                           @field:com.intellij.util.xmlb.annotations.OptionTag("USE_PROJECT_PROFILE") var useProjectProfile: Boolean = true)

  init {
    schemeManager = schemeManagerFactory.create("inspectionProfiles", object : InspectionProfileProcessor() {
      override fun createScheme(dataHolder: SchemeDataHolder<InspectionProfileImpl>, name: String, attributeProvider: Function<String, String?>): InspectionProfileImpl {
        val profile = InspectionProfileImpl(name, InspectionToolRegistrar.getInstance(), this@ProjectInspectionProfileManager, InspectionProfileImpl.getDefaultProfile(), dataHolder)
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
        for (adapter in profileListeners) {
          adapter.profileActivated(oldScheme, newScheme)
        }
      }
    }, isUseOldFileNameSanitize = true)

    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode) {
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
          fireProfilesShutdown()
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

  @TestOnly
  fun forceLoadSchemes() {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode)
    schemeManager.loadSchemes()
  }

  fun isCurrentProfileInitialized() = currentProfile.wasInitialized()

  @Synchronized override fun updateProfile(profile: Profile) {
    super.updateProfile(profile)

    (profile as InspectionProfileImpl).initInspectionTools(project)
  }

  override fun schemeRemoved(scheme: InspectionProfile) {
    scheme.cleanup(project)
  }

  @Suppress("unused")
  private class ProjectInspectionProfileStartUpActivity : StartupActivity {
    override fun runActivity(project: Project) {
      getInstanceImpl(project).apply {
        initialLoadSchemesFuture.done {
          currentProfile.initInspectionTools(project)
          fireProfilesInitialized()

          val app = ApplicationManager.getApplication()
          if (app.isUnitTestMode && app.isDispatchThread) {
            // do not restart daemon in the middle of the test
            //noinspection TestOnlyProblems
            UIUtil.dispatchAllInvocationEvents()
          }
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

  @Synchronized override fun loadState(state: Element) {
    val data = state.getChild("settings")

    val newState = State()

    data?.let {
      try {
        severityRegistrar.readExternal(it)
      }
      catch (e: Throwable) {
        LOG.error(e)
      }

      XmlSerializer.deserializeInto(newState, it)
    }

    this.state = newState

    if (data != null && data.getChild("version")?.getAttributeValue("value") != VERSION) {
      for (o in data.getChildren("option")) {
        if (o.getAttributeValue("name") == "USE_PROJECT_LEVEL_SETTINGS") {
          if (o.getAttributeValue("value").toBoolean()) {
            if (newState.projectProfile != null) {
              currentProfile.convert(data, project)
            }
          }
          break
        }
      }
    }

    if (newState.useProjectProfile) {
      schemeManager.currentSchemeName = newState.projectProfile
    }
  }

  @Synchronized override fun getState(): Element? {
    val result = Element("settings")
    val state = this.state
    state.projectProfile = schemeManager.currentSchemeName
    XmlSerializer.serializeInto(state, result, skipDefaultsSerializationFilter)
    if (!result.children.isEmpty()) {
      result.addContent(Element("version").setAttribute("value", VERSION))
    }

    severityRegistrar.writeExternal(result)
    if (JDOMUtil.isEmpty(result)) {
      result.name = "state"
      return result
    }
    else {
      return Element("state").addContent(result)
    }
  }

  override fun getScopesManager() = scopeManager

  @Synchronized override fun getProfiles(): Collection<Profile> {
    currentProfile
    return schemeManager.allSchemes
  }

  @Synchronized override fun getAvailableProfileNames(): Array<String> = schemeManager.allSchemeNames.toTypedArray()

  val projectProfile: String?
    get() = schemeManager.currentSchemeName

  @Synchronized override fun setRootProfile(name: String?) {
    if (name != schemeManager.currentSchemeName) {
      schemeManager.currentSchemeName = name
      state.useProjectProfile = name != null
    }
  }

  @Synchronized fun setCurrentProfile(profile: InspectionProfileImpl?) {
    schemeManager.setCurrent(profile)
    state.useProjectProfile = profile != null
  }

  @Synchronized override fun getCurrentProfile(): InspectionProfileImpl {
    if (!state.useProjectProfile) {
      return applicationProfileManager.currentProfile as InspectionProfileImpl
    }

    var currentScheme = schemeManager.currentScheme
    if (currentScheme == null) {
      currentScheme = schemeManager.allSchemes.firstOrNull()
      if (currentScheme == null) {
        currentScheme = InspectionProfileImpl(PROJECT_DEFAULT_PROFILE_NAME, InspectionToolRegistrar.getInstance(), this,
                                              InspectionProfileImpl.getDefaultProfile(), null)
        currentScheme.copyFrom(applicationProfileManager.currentProfile)
        currentScheme.isProjectLevel = true
        currentScheme.name = PROJECT_DEFAULT_PROFILE_NAME
        schemeManager.addScheme(currentScheme)
      }
      schemeManager.setCurrent(currentScheme, false)
    }
    return currentScheme
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