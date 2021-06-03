// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.execution.ui.*
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemBeforeRunTask
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.ui.ExternalSystemProjectPathField
import com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments.ExternalSystemTasksAndArguments
import com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments.ExternalSystemTasksAndArgumentsField
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout

abstract class ExternalSystemReifiedRunConfigurationExtension<C : ExternalSystemRunConfiguration>(
  private val runConfigurationClass: Class<C>
) : ExternalSystemRunConfigurationExtension() {

  abstract fun MutableList<SettingsEditorFragment<C, *>>.configureFragments(configuration: C)

  override fun isApplicableFor(configuration: ExternalSystemRunConfiguration): Boolean {
    return runConfigurationClass.isInstance(configuration)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <P : ExternalSystemRunConfiguration> createFragments(configuration: P): List<SettingsEditorFragment<P, *>> {
    val fragments = ArrayList<SettingsEditorFragment<C, *>>()
    fragments.configureFragments(configuration as C)
    return fragments as List<SettingsEditorFragment<P, *>>
  }

  companion object {
    fun <C : ExternalSystemRunConfiguration, K : ExternalSystemBeforeRunTask> createBeforeRun(buildTaskKey: Key<K>): BeforeRunFragment<C> {
      val parentDisposable = Disposer.newDisposable()
      val beforeRunComponent = BeforeRunComponent(parentDisposable)
      val beforeRunFragment = BeforeRunFragment.createBeforeRun<C>(beforeRunComponent, buildTaskKey)
      Disposer.register(beforeRunFragment, parentDisposable)
      return beforeRunFragment
    }

    @Suppress("UNCHECKED_CAST")
    fun <C : ExternalSystemRunConfiguration> createSettingsTag(
      id: String,
      @Nls name: String,
      @Nls group: String,
      @Nls hint: String?,
      getter: (C) -> Boolean,
      setter: (C, Boolean) -> Unit,
      menuPosition: Int
    ): SettingsEditorFragment<C, *> =
      RunConfigurationEditorFragment.createSettingsTag<C>(
        id,
        name,
        group,
        { getter(it.configuration as C) },
        { it, v -> setter(it.configuration as C, v) },
        menuPosition
      ).apply {
        actionHint = hint
      }

    fun <C : ExternalSystemRunConfiguration> createTasksAndArguments(
      project: Project,
      externalSystemId: ProjectSystemId,
      tasksAndArguments: ExternalSystemTasksAndArguments
    ): SettingsEditorFragment<C, ExternalSystemTasksAndArgumentsField> {
      val taskAndArgumentsField = ExternalSystemTasksAndArgumentsField(project, externalSystemId, tasksAndArguments)
      return SettingsEditorFragment<C, ExternalSystemTasksAndArgumentsField>(
        "external.system.tasks.and.arguments.fragment",
        ExternalSystemBundle.message("run.configuration.tasks.and.arguments.name"),
        null,
        taskAndArgumentsField,
        100,
        { it, c -> c.tasksAndArguments = it.tasksAndArguments },
        { it, c -> it.tasksAndArguments = c.tasksAndArguments },
        { true }
      ).apply {
        isCanBeHidden = false
        isRemovable = false
        setHint(ExternalSystemBundle.message("run.configuration.tasks.and.arguments.hint"))
      }
    }

    fun <C : ExternalSystemRunConfiguration> createProjectPath(
      project: Project,
      externalSystemId: ProjectSystemId
    ): SettingsEditorFragment<C, LabeledComponent<ExternalSystemProjectPathField>> {
      val projectPathField = ExternalSystemProjectPathField(project, externalSystemId)
      val projectPathLabel = ExternalSystemBundle.message("run.configuration.project.path.label", externalSystemId.readableName)
      return SettingsEditorFragment<C, LabeledComponent<ExternalSystemProjectPathField>>(
        "external.system.project.path.fragment",
        ExternalSystemBundle.message("run.configuration.project.path.name", externalSystemId.readableName),
        null,
        LabeledComponent.create(projectPathField, projectPathLabel, BorderLayout.WEST),
        -10,
        SettingsEditorFragmentType.EDITOR,
        { it, c -> it.externalProjectPath?.let { p -> c.component.projectPath = p } },
        { it, c -> it.externalProjectPath = FileUtil.toCanonicalPath(c.component.projectPath) },
        { true }
      ).apply {
        isCanBeHidden = false
        isRemovable = false
      }
    }
  }
}