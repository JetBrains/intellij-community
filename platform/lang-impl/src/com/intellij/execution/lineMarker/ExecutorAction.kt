// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.lineMarker

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.actions.*
import com.intellij.execution.configurations.LocatableConfiguration
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.util.Key
import com.intellij.util.containers.mapSmartNotNull

private val LOG = logger<ExecutorAction>()
private val CONFIGURATION_CACHE = Key.create<List<ConfigurationFromContext>>("ConfigurationFromContext")

/**
 * @author Dmitry Avdeev
 */
class ExecutorAction private constructor(private val origin: AnAction,
                                         private val executor: Executor,
                                         private val order: Int) : ActionGroup() {
  init {
    copyFrom(origin)
  }

  companion object {
    @JvmStatic
    @JvmOverloads
    fun getActions(order: Int = 0): Array<AnAction> {
      val actionManager = ActionManager.getInstance()
      return ExecutorRegistry.getInstance().registeredExecutors
        .map { executor -> executor to actionManager.getAction(executor.contextActionId) }
        .filter { pair -> pair.second != null }
        .map { pair -> ExecutorAction(pair.second, pair.first, order) }
        .toTypedArray()
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
        LOG.runAndLogException {
          val configuration = it.createLightConfiguration(context) ?: return@mapSmartNotNull null
          val settings = RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(context.project), configuration, false)
          ConfigurationFromContextImpl(it, settings, context.psiLocation)
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val name = getActionName(e.dataContext)
    e.presentation.isEnabledAndVisible = name != null
    origin.update(e)
    e.presentation.text = name
  }

  override fun actionPerformed(e: AnActionEvent) {
    origin.actionPerformed(e)
  }

  override fun canBePerformed(context: DataContext): Boolean = origin !is ActionGroup || origin.canBePerformed(context)

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = (origin as? ActionGroup)?.getChildren(e) ?: AnAction.EMPTY_ARRAY

  override fun isDumbAware(): Boolean = origin.isDumbAware

  override fun isPopup(): Boolean = origin !is ActionGroup || origin.isPopup

  override fun hideIfNoVisibleChildren(): Boolean = origin is ActionGroup && origin.hideIfNoVisibleChildren()

  override fun disableIfNoVisibleChildren(): Boolean = origin !is ActionGroup || origin.disableIfNoVisibleChildren()

  fun getActionName(dataContext: DataContext): String? {
    val list = getConfigurations(dataContext)
    if (list.isEmpty()) {
      return null
    }

    val configuration = list.getOrNull(if (order < list.size) order else 0)?.configuration as LocatableConfiguration
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