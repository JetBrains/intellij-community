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
package com.intellij.execution.lineMarker

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
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

/**
 * @author Dmitry Avdeev
 */
class ExecutorAction private constructor(private val myOrigin: AnAction,
                                         private val myExecutor: Executor,
                                         private val myOrder: Int) : AnAction() {

  init {
    copyFrom(myOrigin)
  }

  companion object {
    private val LOG = logger<ExecutorAction>()
    private val CONFIGURATION_CACHE = Key.create<List<ConfigurationFromContext>>("ConfigurationFromContext")

    @JvmStatic
    fun getActions(order: Int): Array<AnAction> {
      return ExecutorRegistry.getInstance().registeredExecutors.map {
        ExecutorAction(ActionManager.getInstance().getAction(it.contextActionId), it, order)
      }.toTypedArray()
    }

    private fun getConfigurations(dataContext: DataContext): List<ConfigurationFromContext> {
      var result = DataManager.getInstance().loadFromDataContext(dataContext, CONFIGURATION_CACHE)
      if (result == null) {
        result = calcConfigurations(dataContext)
        DataManager.getInstance().saveInDataContext(dataContext, CONFIGURATION_CACHE, result)
      }
      return result
    }

    private fun calcConfigurations(dataContext: DataContext): List<ConfigurationFromContext> {
      val context = ConfigurationContext.getFromContext(dataContext)
      if (context.location == null) {
        return emptyList()
      }
      return RunConfigurationProducer.getProducers(context.project).mapNotNull {
        LOG.catchAndLog {
          val configuration = it.createLightConfiguration(context) ?: return@mapNotNull null
          val settings = RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(context.project), configuration, false)
          ConfigurationFromContextImpl(it, settings, context.psiLocation)
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val name = getActionName(e.dataContext, myExecutor)
    e.presentation.isVisible = name != null
    e.presentation.text = name
  }

  override fun actionPerformed(e: AnActionEvent) {
    myOrigin.actionPerformed(e)
  }

  private fun getActionName(dataContext: DataContext, executor: Executor): String? {
    val list = getConfigurations(dataContext)
    if (list.isEmpty()) {
      return null
    }
    val configuration = list[if (myOrder < list.size) myOrder else 0]
    val actionName = BaseRunConfigurationAction.suggestRunActionName(configuration.configuration as LocatableConfiguration)
    return executor.getStartActionText(actionName)
  }
}
