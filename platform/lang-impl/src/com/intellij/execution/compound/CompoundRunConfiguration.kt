/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.compound

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.RunnerIconProvider
import com.intellij.execution.configurations.*
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import gnu.trove.THashSet
import org.jdom.Element
import java.util.*
import javax.swing.Icon

private data class TypeAndName(val type: String, val name: String)

class CompoundRunConfiguration(project: Project, type: CompoundRunConfigurationType, name: String) : RunConfigurationBase(project,
                                                                                                                          type.configurationFactories[0],
                                                                                                                          name), RunnerIconProvider, WithoutOwnBeforeRunSteps, Cloneable {
  companion object {
    @JvmField
    val COMPARATOR = Comparator<RunConfiguration> { o1, o2 ->
      val i = o1.type.displayName.compareTo(o2.type.displayName)
      when {
        i != 0 -> i
        else -> o1.name.compareTo(o2.name)
      }
    }
  }

  // we cannot compute setToRun on read because we need type.displayName to sort, but to get type we need runManager - it is prohibited to get runManager in the readExternal
  private var unsortedConfigurationTypeAndNames: List<TypeAndName> = emptyList()

  // have to use RunConfiguration instead of RunnerAndConfigurationSettings because setConfigurations (called from CompoundRunConfigurationSettingsEditor.applyEditorTo) cannot use RunnerAndConfigurationSettings
  private var sortedConfigurations = TreeSet<RunConfiguration>(COMPARATOR)
  private var isInitialized = false

  @JvmOverloads
  fun getConfigurations(runManager: RunManagerImpl? = null): Collection<RunConfiguration> {
    initIfNeed(runManager)
    return sortedConfigurations
  }

  fun setConfigurations(value: Collection<RunConfiguration>) {
    // invalidate, we don't use it
    unsortedConfigurationTypeAndNames = emptyList()
    sortedConfigurations.clear()
    sortedConfigurations.addAll(value)
  }

  private fun initIfNeed(_runManager: RunManagerImpl?) {
    if (isInitialized) {
      return
    }

    sortedConfigurations.clear()

    val runManager = _runManager ?: RunManagerImpl.getInstanceImpl(project)
    for ((type, name) in unsortedConfigurationTypeAndNames) {
      val settings = runManager.findConfigurationByTypeAndName(type, name)
      if (settings != null && settings.configuration !== this) {
        sortedConfigurations.add(settings.configuration)
      }
    }
    isInitialized = true
  }

  override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
    return CompoundRunConfigurationSettingsEditor(project)
  }

  @Throws(RuntimeConfigurationException::class)
  override fun checkConfiguration() {
    if (sortedConfigurations.isEmpty()) {
      throw RuntimeConfigurationException("There is nothing to run")
    }
  }

  @Throws(ExecutionException::class)
  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
    try {
      checkConfiguration()
    }
    catch (e: RuntimeConfigurationException) {
      throw ExecutionException(e.message)
    }

    return RunProfileState { _, _ ->
      ApplicationManager.getApplication().invokeLater {
        val runManager = RunManagerImpl.getInstanceImpl(project)
        for (configuration in sortedConfigurations) {
          runManager.getSettings(configuration)?.let {
            ExecutionUtil.runConfiguration(it, executor)
          }
        }
      }
      null
    }
  }

  override fun readExternal(element: Element) {
    super.readExternal(element)

    val children = element.getChildren("toRun")
    if (children.isEmpty()) {
      unsortedConfigurationTypeAndNames = emptyList()
      return
    }

    val list = THashSet<TypeAndName>()
    for (child in children) {
      val type = child.getAttributeValue("type") ?: continue
      val name = child.getAttributeValue("name") ?: continue
      list.add(TypeAndName(type, name))
    }

    unsortedConfigurationTypeAndNames = list.toList()
  }

  override fun writeExternal(element: Element) {
    super.writeExternal(element)

    for (configuration in sortedConfigurations) {
      val child = Element("toRun")
      child.setAttribute("type", configuration.type.id)
      child.setAttribute("name", configuration.name)
      element.addContent(child)
    }
  }

  override fun clone(): RunConfiguration {
    val clone = super<RunConfigurationBase>.clone() as CompoundRunConfiguration
    clone.unsortedConfigurationTypeAndNames = unsortedConfigurationTypeAndNames.toList()
    clone.sortedConfigurations = TreeSet(COMPARATOR)
    clone.sortedConfigurations.addAll(sortedConfigurations)
    return clone
  }

  override fun getExecutorIcon(configuration: RunConfiguration, executor: Executor): Icon? {
    return if (DefaultRunExecutor.EXECUTOR_ID == executor.id && hasRunningSingletons()) {
      AllIcons.Actions.Restart
    }
    else executor.icon
  }

  private fun hasRunningSingletons(): Boolean {
    val project = project
    if (project.isDisposed) {
      return false
    }

    return ExecutionManagerImpl.getInstance(project).getRunningDescriptors { s ->
      val manager = RunManagerImpl.getInstanceImpl(project)
      for (configuration in sortedConfigurations) {
        if (configuration is CompoundRunConfiguration && configuration.hasRunningSingletons()) {
          return@getRunningDescriptors true
        }

        val settings = manager.findConfigurationByTypeAndName(configuration.type.id, configuration.name)
        if (settings != null && settings.isSingleton && configuration == s.configuration) {
          return@getRunningDescriptors true
        }
      }
      false
    }.isNotEmpty()
  }
}
