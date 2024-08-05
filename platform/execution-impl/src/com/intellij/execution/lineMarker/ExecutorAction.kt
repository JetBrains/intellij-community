// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.lineMarker

import com.intellij.execution.Executor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus


/**
 * @param order corresponding sorting happens here:
 * [com.intellij.execution.actions.BaseRunConfigurationAction.getOrderedConfiguration]
 */
class ExecutorAction private constructor(val origin: AnAction,
                                         val executor: Executor,
                                         val order: Int) :
  ActionGroup(), DataSnapshotProvider, ActionWithDelegate<AnAction> {
  init {
    copyFrom(origin)
    if (origin !is ActionGroup) {
      templatePresentation.isPerformGroup = true
      templatePresentation.isPopupGroup = true
    }
  }

  override fun getDelegate(): AnAction = origin

  override fun isDumbAware() = origin.isDumbAware

  override fun getActionUpdateThread() = ActionWrapperUtil.getActionUpdateThread(this, origin)

  override fun dataSnapshot(sink: DataSink) {
    if (order != 0) {
      sink[orderKey] = order
    }
  }

  companion object {
    private val LOG = logger<ExecutorAction>()

    @JvmStatic
    val orderKey: DataKey<Int> = DataKey.create("Order")

    @JvmStatic
    @JvmOverloads
    fun getActions(order: Int = 0) = getActionList(order).toTypedArray()

    @JvmStatic
    @JvmOverloads
    fun getActionList(order: Int = 0): List<AnAction> {
      val actionManager = ActionManager.getInstance()

      val extraActionsGroup = actionManager.getAction("RunLineMarkerExtraActions")
      val extraActions = (extraActionsGroup as? DefaultActionGroup)?.getChildren(actionManager) ?: run {
        LOG.error("extraActionsGroup doesn't inherit DefaultActionGroup. extraActionsGroup.class=${extraActionsGroup.javaClass.name}")
        emptyArray<AnAction>()
      }
      val extensions = Executor.EXECUTOR_EXTENSION_NAME.extensionList
      val result = ArrayList<AnAction>(extensions.size + extraActions.size)

      extensions
        .mapNotNullTo(result) { executor ->
          actionManager.getAction(executor.contextActionId)?.let {
            ExecutorAction(it, executor, order)
          }
        }

      for (extraAction in extraActions) {
        if (extraAction is ActionGroup) {
          result.add(object : ActionGroupWrapper(extraAction), DataSnapshotProvider {
            override fun dataSnapshot(sink: DataSink) {
              sink[orderKey] = order
            }

            override fun equals(other: Any?): Boolean {
              return other is ActionGroupWrapper && delegate == other.delegate
            }

            override fun hashCode(): Int {
              return delegate.hashCode()
            }
          })
        }
      }

      return result
    }

    @ApiStatus.Internal
    @JvmStatic
    fun wrap(runContextAction: AnAction, executor: Executor, order: Int): AnAction {
      return ExecutorAction(runContextAction, executor, order)
    }
  }

  override fun update(e: AnActionEvent) {
    ActionWrapperUtil.update(e, this, origin)
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (origin !is ActionGroup) return EMPTY_ARRAY
    return ActionWrapperUtil.getChildren(e, this, origin)
  }

  override fun actionPerformed(e: AnActionEvent) {
    ActionWrapperUtil.actionPerformed(e, this, origin)
  }

  // for EquatableTooltipProvider
  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is ExecutorAction) {
      return false
    }

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