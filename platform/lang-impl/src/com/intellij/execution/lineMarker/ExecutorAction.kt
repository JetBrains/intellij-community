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
package com.intellij.execution.lineMarker

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunnerRegistry
import com.intellij.execution.actions.*
import com.intellij.execution.configurations.LocatableConfiguration
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.catchAndLog
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.util.containers.mapSmart
import com.intellij.util.containers.mapSmartNotNull

private val LOG = logger<ExecutorAction>()
private val CONFIGURATION_CACHE = Key.create<List<ConfigurationFromContext>>("ConfigurationFromContext")

/**
 * @author Dmitry Avdeev
 */
class ExecutorAction private constructor(private val origin: AnAction, private val executor: Executor, private val order: Int) : AnAction() {
  init {
    copyFrom(origin)
  }

  companion object {
    @JvmStatic
    @JvmOverloads
    fun getActions(order: Int = 0) = getActionList(order).toTypedArray()

    @JvmStatic
    @JvmOverloads
    fun getActionList(order: Int = 0): List<AnAction> {
      val actionManager = ActionManager.getInstance()
      return ExecutorRegistry.getInstance().registeredExecutors.mapSmart {
        ExecutorAction(actionManager.getAction(it.contextActionId), it, order)
      }
    }

    private fun getConfigurations(dataContext: DataContext): List<ConfigurationFromContext> {
      var result = DataManager.getInstance().loadFromDataContext(dataContext, CONFIGURATION_CACHE)
      if (result == null) {
        result = computeConfigurations(dataContext)
        DataManager.getInstance().saveInDataContext(dataContext, CONFIGURATION_CACHE, result)
      }
      return result
    }

    private fun computeConfigurations(dataContext: DataContext): List<ConfigurationFromContext> {
      val context = ConfigurationContext.getFromContext(dataContext)
      if (context.location == null) {
        return emptyList()
      }

      return RunConfigurationProducer.getProducers(context.project).mapSmartNotNull {
        LOG.catchAndLog {
          val configuration = it.createLightConfiguration(context) ?: return@mapSmartNotNull null
          val settings = RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(context.project), configuration, false)
          ConfigurationFromContextImpl(it, settings, context.psiLocation)
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val name = getActionName(e.dataContext, executor)
    e.presentation.isEnabledAndVisible = name != null
    e.presentation.text = name
  }

  override fun actionPerformed(e: AnActionEvent) {
    origin.actionPerformed(e)
  }

  private fun getActionName(dataContext: DataContext, executor: Executor): String? {
    val list = getConfigurations(dataContext)
    if (list.isEmpty()) {
      return null
    }

    val configuration = list.getOrNull(if (order < list.size) order else 0)?.configuration as LocatableConfiguration
    if (RunnerRegistry.getInstance().getRunner(executor.id, configuration) == null) {
      return null
    }

    return executor.getStartActionText(BaseRunConfigurationAction.suggestRunActionName(configuration))
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other?.javaClass != javaClass) return false

    other as ExecutorAction

    if (origin != other.origin) return false
    if (executor != other.executor) return false
    if (order != other.order) return false

    return true
  }

  override fun hashCode(): Int {
    var result = origin.hashCode()
    result = 31 * result + executor.hashCode()
    result = 31 * result + order
    return result
  }
}
