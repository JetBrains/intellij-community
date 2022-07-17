// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.model.SideEffectGuard
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.util.Consumer

open class InspectionProfileModifiableModel(val source: InspectionProfileImpl) : InspectionProfileImpl(source.name, source.myToolSupplier, source.profileManager, source.myBaseProfile, null) {
  private var modified = false

  init {
    myUninitializedSettings.putAll(source.myUninitializedSettings)
    isProjectLevel = source.isProjectLevel
    myLockedProfile = source.myLockedProfile
    copyFrom(source)
  }

  fun isChanged(): Boolean = modified || source.myLockedProfile != myLockedProfile

  fun setModified(value: Boolean) {
    modified = value
  }

  override fun resetToBase(toolId: String, scope: NamedScope?, project: Project) {
    super.resetToBase(toolId, scope, project)
    setModified(true)
  }

  override fun copyToolsConfigurations(project: Project?) {
    copyToolsConfigurations(source, project)
  }

  override fun createTools(project: Project?) = source.getDefaultStates(project).map { it.tool }

  private fun copyToolsConfigurations(profile: InspectionProfileImpl, project: Project?) {
    try {
      for (toolList in profile.myTools.values) {
        val tools = myTools[toolList.shortName]!!
        val defaultState = toolList.defaultState
        tools.setDefaultState(copyToolSettings(defaultState.tool), defaultState.isEnabled, defaultState.level, defaultState.editorAttributesExternalName)
        tools.removeAllScopes()
        val nonDefaultToolStates = toolList.nonDefaultTools
        if (nonDefaultToolStates != null) {
          for (state in nonDefaultToolStates) {
            val toolWrapper = copyToolSettings(state.tool)
            val scope = state.getScope(project)
            val tool = if (scope == null) {
              tools.addTool(state.scopeName, toolWrapper, state.isEnabled, state.level)
            }
            else {
              tools.addTool(scope, toolWrapper, state.isEnabled, state.level)
            }
            state.editorAttributesKey?.externalName?.let { tool.setEditorAttributesExternalName(it) }
          }
        }
        tools.isEnabled = toolList.isEnabled
      }
    }
    catch (e: WriteExternalException) {
      LOG.error(e)
    }
    catch (e: InvalidDataException) {
      LOG.error(e)
    }
  }

  fun isProperSetting(toolId: String): Boolean {
    if (myBaseProfile != null) {
      val tools = myBaseProfile.getToolsOrNull(toolId, null)
      val currentTools = myTools[toolId]
      return tools != currentTools
    }
    return false
  }

  fun isProperSetting(toolId: String, scope: NamedScope, project: Project): Boolean {
    if (myBaseProfile != null) {
      val baseDefaultWrapper = myBaseProfile.getToolsOrNull(toolId, null)?.defaultState?.tool
      val actualWrapper = myTools[toolId]?.tools?.first { s -> scope == s.getScope(project) }?.tool
      return baseDefaultWrapper != null && actualWrapper != null && ScopeToolState.areSettingsEqual(baseDefaultWrapper, actualWrapper)
    }
    return false
  }

  fun resetToBase(project: Project?) {
    initInspectionTools(project)

    copyToolsConfigurations(myBaseProfile, project)
    myChangedToolNames = null
    myUninitializedSettings.clear()
  }

  //invoke when isChanged() == true
  fun commit() {
    source.commit(this)
    modified = false
  }

  fun resetToEmpty(project: Project) {
    initInspectionTools(project)
    for (toolWrapper in getInspectionTools(null)) {
      setToolEnabled(toolWrapper.shortName, false, project, fireEvents = false)
    }
  }

  private fun InspectionProfileImpl.commit(model: InspectionProfileImpl) {
    name = model.name
    description = model.description
    isProjectLevel = model.isProjectLevel
    myLockedProfile = model.myLockedProfile
    myChangedToolNames = null
    if (model.wasInitialized()) {
      myTools = model.myTools
    }
    profileManager = model.profileManager
    scopesOrder = model.scopesOrder
  }

  fun disableTool(toolShortName: String, element: PsiElement) {
    getTools(toolShortName, element.project).disableTool(element)
  }

  override fun toString(): String = "$name (copy)"
}

fun modifyAndCommitProjectProfile(project: Project, action: Consumer<InspectionProfileModifiableModel>) {
  InspectionProjectProfileManager.getInstance(project).currentProfile.edit { action.consume(this) }
}

inline fun InspectionProfileImpl.edit(task: InspectionProfileModifiableModel.() -> Unit) {
  SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.SETTINGS)
  val model = InspectionProfileModifiableModel(this)
  model.task()
  model.commit()
  profileManager.fireProfileChanged(this)
}
