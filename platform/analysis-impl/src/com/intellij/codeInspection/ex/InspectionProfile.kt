// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.ex.InspectionProfileImpl.INIT_INSPECTIONS
import com.intellij.configurationStore.SerializableScheme
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.project.Project
import com.intellij.profile.ProfileEx
import com.intellij.profile.codeInspection.BaseInspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.ProjectBasedInspectionProfileManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.util.xmlb.annotations.Transient
import org.jdom.Element

const val DEFAULT_PROFILE_NAME: String = "Default"

val BASE_PROFILE: InspectionProfileImpl by lazy { InspectionProfileImpl(DEFAULT_PROFILE_NAME) }

abstract class NewInspectionProfile(name: String, private var profileManager: BaseInspectionProfileManager) : ProfileEx(name), InspectionProfile, SerializableScheme {
  @Volatile
  @JvmField
  protected var initialized: Boolean = false
  @JvmField
  protected val lock: Any = Any()

  private var isProjectLevel: Boolean = false

  @JvmField
  @Transient
  internal var schemeState: SchemeState? = null

  override fun getSchemeState(): SchemeState? = schemeState

  @Transient
  fun isProjectLevel(): Boolean = isProjectLevel

  fun setProjectLevel(value: Boolean) {
    isProjectLevel = value
  }

  @Transient
  fun getProfileManager() = profileManager

  fun setProfileManager(value: BaseInspectionProfileManager) {
    profileManager = value
  }

  fun wasInitialized(): Boolean {
    return initialized
  }

  abstract override fun getDisplayName(): String

  protected val pathMacroManager: PathMacroManager
    get() {
      val profileManager = profileManager
      return PathMacroManager.getInstance((profileManager as? ProjectBasedInspectionProfileManager)?.project ?: ApplicationManager.getApplication())
    }

  override fun toString(): String = name

  override fun equals(other: Any?): Boolean = super.equals(other) && (other as NewInspectionProfile).profileManager === profileManager

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + profileManager.hashCode()
    return result
  }

  /**
   * If you need to enable multiple tools, please use [InspectionProfileImpl.modifyProfile].
   */
  @JvmOverloads
  fun setToolEnabled(toolShortName: String, enabled: Boolean, project: Project? = null, fireEvents: Boolean = true) {
    val tools = getTools(toolShortName, project ?: (profileManager as? ProjectBasedInspectionProfileManager)?.project)
    if (enabled) {
      if (tools.isEnabled && tools.defaultState.isEnabled) {
        return
      }

      tools.isEnabled = true
      tools.defaultState.isEnabled = true
      schemeState = SchemeState.POSSIBLY_CHANGED
    }
    else {
      tools.isEnabled = false
      if (tools.nonDefaultTools == null) {
        tools.defaultState.isEnabled = false
      }
      schemeState = SchemeState.POSSIBLY_CHANGED
    }

    if (fireEvents) {
      profileManager.fireProfileChanged(this as InspectionProfileImpl)
    }
  }

  fun getTools(name: String, project: Project?): ToolsImpl = getToolsOrNull(name, project) ?: throw AssertionError("Can't find tools for \"$name\" in the profile \"${this.name}\"")

  abstract fun getToolsOrNull(name: String, project: Project?): ToolsImpl?

  @JvmOverloads
  fun initInspectionTools(project: Project? = (profileManager as? ProjectBasedInspectionProfileManager)?.project) {
    if (initialized || !forceInitInspectionTools()) {
      return
    }

    synchronized(lock) {
      if (!initialized) {
        initialize(project)
      }
    }
  }

  protected open fun forceInitInspectionTools(): Boolean = !ApplicationManager.getApplication().isUnitTestMode || INIT_INSPECTIONS

  protected abstract fun initialize(project: Project?)

  fun copyFrom(profile: InspectionProfileImpl) {
    var element = profile.writeScheme()
    if (element.name == "component") {
      element = element.getChild("profile")
    }
    readExternal(element)
  }
  abstract fun readExternal(element: Element)
}

fun createSimple(name: String, project: Project, toolWrappers: List<InspectionToolWrapper<*, *>>): InspectionProfileImpl {
  val profile = InspectionProfileImpl(name, InspectionToolsSupplier.Simple(toolWrappers), InspectionProfileManager.getInstance() as BaseInspectionProfileManager)
  for (toolWrapper in toolWrappers) {
    profile.enableTool(toolWrapper.shortName, project)
  }
  return profile
}