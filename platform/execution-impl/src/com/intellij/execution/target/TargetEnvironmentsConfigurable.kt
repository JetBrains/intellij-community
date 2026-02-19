// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.options.MasterDetails
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DetailsComponent
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class TargetEnvironmentsConfigurable(private val project: Project,
                                     initialSelectedName: String? = null,
                                     defaultLanguageRuntime: LanguageRuntimeType<*>? = null)
  : SearchableConfigurable, MasterDetails {

  constructor(project: Project) : this(project, null, null)

  private val editor = TargetEnvironmentsMasterDetails(project, initialSelectedName, defaultLanguageRuntime)

  override fun initUi() = editor.initUi()

  override fun getToolbar(): JComponent = editor.toolbar

  override fun getMaster(): JComponent = editor.master

  override fun getDetails(): DetailsComponent = editor.details

  override fun isModified(): Boolean = editor.isModified

  override fun getId(): String = "Runtime.Targets.Configurable"

  override fun getDisplayName(): String = ExecutionBundle.message("configurable.name.runtime.targets")

  override fun createComponent(): JComponent = editor.createComponent()

  override fun getHelpTopic(): String = "reference.run.targets"

  override fun apply() {
    editor.apply()
  }

  override fun reset() {
    editor.reset()
  }

  override fun disposeUIResources() {
    editor.disposeUIResources()
    super.disposeUIResources()
  }

  fun openForEditing(): Boolean {
    return ShowSettingsUtil.getInstance().editConfigurable(project, DIMENSION_KEY, this, true)
  }

  val selectedTargetConfig: TargetEnvironmentConfiguration? get() = editor.selectedConfig

  companion object {
    @JvmStatic
    private val DIMENSION_KEY = TargetEnvironmentsConfigurable::class.simpleName + ".size"
  }
}