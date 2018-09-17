// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.compound

import com.intellij.execution.*
import com.intellij.execution.configurations.*
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.impl.compareTypesForUi
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import gnu.trove.THashSet
import org.jdom.Element
import java.util.*
import javax.swing.Icon

data class SettingsAndEffectiveTarget(val settings: RunnerAndConfigurationSettings, val target: ExecutionTarget)

class CompoundRunConfiguration @JvmOverloads constructor(name: String, project: Project, factory: ConfigurationFactory = runConfigurationType<CompoundRunConfigurationType>()) :
  RunConfigurationMinimalBase(name, factory, project), RunnerIconProvider, WithoutOwnBeforeRunSteps, Cloneable {
  companion object {
    @JvmField
    internal val COMPARATOR: Comparator<RunConfiguration> = Comparator { o1, o2 ->
      val compareTypeResult = compareTypesForUi(o1.type, o2.type)
      if (compareTypeResult == 0) o1.name.compareTo(o2.name) else compareTypeResult
    }
  }

  // we cannot compute setToRun on read because we need type.displayName to sort, but to get type we need runManager - it is prohibited to get runManager in the readExternal
  private var unsortedConfigurations: List<TypeNameTarget> = emptyList()

  // have to use RunConfiguration instead of RunnerAndConfigurationSettings because setConfigurations (called from CompoundRunConfigurationSettingsEditor.applyEditorTo) cannot use RunnerAndConfigurationSettings
  private var sortedConfigurationsWithTargets = TreeMap<RunConfiguration, ExecutionTarget?>(COMPARATOR)
  private var isInitialized = false

  fun getConfigurationsWithTargets(runManager: RunManagerImpl): Map<RunConfiguration, ExecutionTarget?> {
    initIfNeed(runManager)
    return sortedConfigurationsWithTargets
  }

  fun setConfigurationsWithTargets(value: Map<RunConfiguration, ExecutionTarget?>) {
    markInitialized()

    sortedConfigurationsWithTargets.clear()
    sortedConfigurationsWithTargets.putAll(value)
  }

  fun setConfigurationsWithoutTargets(value: Collection<RunConfiguration>) {
    setConfigurationsWithTargets(value.associate { it to null })
  }

  private fun initIfNeed(runManager: RunManagerImpl) {
    if (isInitialized) {
      return
    }

    sortedConfigurationsWithTargets.clear()

    val targetManager = ExecutionTargetManager.getInstance(project) as ExecutionTargetManagerImpl

    for ((type, name, targetId) in unsortedConfigurations) {
      val settings = runManager.findConfigurationByTypeAndName(type, name)
      if (settings != null && settings.configuration !== this) {
        val target = targetId?.let { targetManager.findTargetByIdFor(settings, it) }
        sortedConfigurationsWithTargets.put(settings.configuration, target)
      }
    }

    markInitialized()
  }

  private fun markInitialized() {
    unsortedConfigurations = emptyList()
    isInitialized = true
  }

  override fun getConfigurationEditor() = CompoundRunConfigurationSettingsEditor(project)

  override fun checkConfiguration() {
    if (sortedConfigurationsWithTargets.isEmpty()) {
      throw RuntimeConfigurationException("There is nothing to run")
    }

    val temp = RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), this)
    if (ExecutionTargetManager.getInstance(project).getTargetsFor(temp).isEmpty()) {
      throw RuntimeConfigurationException("No suitable targets to run on; please choose a target for each configuration")
    }
  }

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
    try {
      checkConfiguration()
    }
    catch (e: RuntimeConfigurationException) {
      throw ExecutionException(e.message)
    }

    promptUserToUseRunDashboard(
      project,
      getConfigurationsWithEffectiveRunTargets().map { it.settings.configuration.type }
    )

    return RunProfileState { _, _ ->
      ApplicationManager.getApplication().invokeLater {
        val groupId = ExecutionEnvironment.getNextUnusedExecutionId()
        for ((settings, target) in getConfigurationsWithEffectiveRunTargets()) {
          ExecutionUtil.runConfiguration(settings, executor, target, groupId)
        }
      }
      null
    }
  }

  fun getConfigurationsWithEffectiveRunTargets(): List<SettingsAndEffectiveTarget> {
    val runManager = RunManagerImpl.getInstanceImpl(project)
    val activeTarget = ExecutionTargetManager.getActiveTarget(project)
    val defaultTarget = DefaultExecutionTarget.INSTANCE

    return sortedConfigurationsWithTargets.mapNotNull { (configuration, specifiedTarget) ->
      runManager.getSettings(configuration)?.let {
        val effectiveTarget = specifiedTarget ?: if (ExecutionTargetManager.canRun(it, activeTarget)) activeTarget else defaultTarget
        SettingsAndEffectiveTarget(it, effectiveTarget)
      }
    }
  }

  override fun readExternal(element: Element) {
    super.readExternal(element)

    val children = element.getChildren("toRun")
    if (children.isEmpty()) {
      unsortedConfigurations = emptyList()
      return
    }

    val list = THashSet<TypeNameTarget>()
    for (child in children) {
      val type = child.getAttributeValue("type") ?: continue
      val name = child.getAttributeValue("name") ?: continue
      list.add(TypeNameTarget(type, name, child.getAttributeValue("targetId")))
    }

    unsortedConfigurations = list.toList()
  }

  override fun writeExternal(element: Element) {
    super.writeExternal(element)

    for ((configuration, target) in sortedConfigurationsWithTargets) {
      val child = Element("toRun")
      child.setAttribute("type", configuration.type.id)
      child.setAttribute("name", configuration.name)
      target?.let { child.setAttribute("targetId", it.id) }
      element.addContent(child)
    }
  }

  override fun clone(): RunConfiguration {
    val clone = CompoundRunConfiguration(name, project, factory)
    clone.unsortedConfigurations = unsortedConfigurations
    clone.sortedConfigurationsWithTargets = TreeMap(COMPARATOR)
    clone.sortedConfigurationsWithTargets.putAll(sortedConfigurationsWithTargets)
    return clone
  }

  override fun getExecutorIcon(configuration: RunConfiguration, executor: Executor): Icon? {
    return when {
      DefaultRunExecutor.EXECUTOR_ID == executor.id && hasRunningSingletons() -> AllIcons.Actions.Restart
      else -> executor.icon
    }
  }

  private fun hasRunningSingletons(): Boolean {
    val project = project
    if (project.isDisposed) {
      return false
    }

    return ExecutionManagerImpl.getInstance(project).getRunningDescriptors { s ->
      val manager = RunManagerImpl.getInstanceImpl(project)
      for ((configuration, _) in sortedConfigurationsWithTargets) {
        if (configuration is CompoundRunConfiguration && configuration.hasRunningSingletons()) {
          return@getRunningDescriptors true
        }

        val settings = manager.findSettings(configuration)
        if (settings != null && !settings.configuration.isAllowRunningInParallel && configuration == s.configuration) {
          return@getRunningDescriptors true
        }
      }
      false
    }.isNotEmpty()
  }
}

internal data class TypeNameTarget(val type: String, val name: String, val targetId: String?)