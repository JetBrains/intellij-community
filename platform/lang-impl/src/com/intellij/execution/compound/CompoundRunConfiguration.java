/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.*
import com.intellij.execution.configurations.*
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.runners.ProgramRunner
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.WriteExternalException
import gnu.trove.THashSet
import org.jdom.Element

import javax.swing.*
import java.util.*

class CompoundRunConfiguration(project: Project, type: CompoundRunConfigurationType, name: String) : RunConfigurationBase(project,
                                                                                                                          type.configurationFactories[0],
                                                                                                                          name), RunnerIconProvider, WithoutOwnBeforeRunSteps, Cloneable {
  private var myPairs: MutableSet<Pair<String, String>> = THashSet()
  private var mySetToRun: MutableSet<RunConfiguration> = TreeSet(COMPARATOR)
  private var myInitialized = false

  val setToRun: Set<RunConfiguration>
    get() = getSetToRun(null)

  fun getSetToRun(runManager: RunManagerImpl?): Set<RunConfiguration> {
    initIfNeed(runManager)
    return mySetToRun
  }

  private fun initIfNeed(runManager: RunManagerImpl?) {
    var runManager = runManager
    if (myInitialized) {
      return
    }

    mySetToRun.clear()

    if (runManager == null) {
      runManager = RunManagerImpl.getInstanceImpl(project)
    }

    for (pair in myPairs) {
      val settings = runManager.findConfigurationByTypeAndName(pair.first, pair.second)
      if (settings != null && settings.configuration !== this) {
        mySetToRun.add(settings.configuration)
      }
    }
    myInitialized = true
  }

  override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
    return CompoundRunConfigurationSettingsEditor(project)
  }

  @Throws(RuntimeConfigurationException::class)
  override fun checkConfiguration() {
    if (setToRun.isEmpty()) {
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

    return RunProfileState { executor, runner ->
      ApplicationManager.getApplication().invokeLater {
        val manager = RunManagerImpl.getInstanceImpl(project)
        for (configuration in setToRun) {
          val settings = manager.getSettings(configuration)
          if (settings != null) {
            ExecutionUtil.runConfiguration(settings, executor)
          }
        }
      }
      null
    }
  }

  @Throws(InvalidDataException::class)
  override fun readExternal(element: Element) {
    super.readExternal(element)
    myPairs.clear()
    val children = element.getChildren("toRun")
    for (child in children) {
      val type = child.getAttributeValue("type")
      val name = child.getAttributeValue("name")
      if (type != null && name != null) {
        myPairs.add(Pair.create(type, name))
      }
    }
  }

  @Throws(WriteExternalException::class)
  override fun writeExternal(element: Element) {
    super.writeExternal(element)
    for (configuration in setToRun) {
      val child = Element("toRun")
      child.setAttribute("type", configuration.type.id)
      child.setAttribute("name", configuration.name)
      element.addContent(child)
    }
  }

  override fun clone(): RunConfiguration {
    val clone = super.clone() as CompoundRunConfiguration
    clone.myPairs = THashSet(myPairs)
    clone.mySetToRun = TreeSet(COMPARATOR)
    clone.mySetToRun.addAll(setToRun)
    return clone
  }

  override fun getExecutorIcon(configuration: RunConfiguration, executor: Executor): Icon? {
    return if (DefaultRunExecutor.EXECUTOR_ID == executor.id && hasRunningSingletons()) {
      AllIcons.Actions.Restart
    }
    else executor.icon
  }

  protected fun hasRunningSingletons(): Boolean {
    val project = project
    if (project.isDisposed) return false
    val executionManager = ExecutionManagerImpl.getInstance(project)

    return executionManager.getRunningDescriptors { s ->
      val manager = RunManagerImpl.getInstanceImpl(project)
      for (runConfiguration in mySetToRun) {
        if (runConfiguration is CompoundRunConfiguration && runConfiguration.hasRunningSingletons()) {
          return@executionManager.getRunningDescriptors true
        }
        val settings = manager.findConfigurationByTypeAndName(runConfiguration.type.id, runConfiguration.name)
        if (settings != null && settings.isSingleton && runConfiguration == s.configuration) {
          return@executionManager.getRunningDescriptors true
        }
      }
      false
    }.size > 0
  }

  companion object {
    internal val COMPARATOR = { o1, o2 ->
      val i = o1.getType().getDisplayName().compareTo(o2.getType().getDisplayName())
      if (i != 0) i else o1.getName().compareTo(o2.getName())
    }
  }
}
