// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.lineMarker

import com.intellij.execution.Executor
import com.intellij.openapi.actionSystem.*
import org.jetbrains.annotations.ApiStatus


/**
 * @param order corresponding sorting happens here:
 * [com.intellij.execution.actions.BaseRunConfigurationAction.getOrderedConfiguration]
 */
class ExecutorAction private constructor(val origin: AnAction,
                                         val executor: Executor,
                                         val order: Int) :
  ActionGroup(), ActionWithDelegate<AnAction> {
  init {
    copyFrom(origin)
    if (origin !is ActionGroup) {
      templatePresentation.isPerformGroup = true
      templatePresentation.isPopupGroup = true
    }
  }

  override fun getActionUpdateThread() = origin.actionUpdateThread

  override fun getDelegate(): AnAction {
    return origin
  }

  override fun isDumbAware() = origin.isDumbAware

  companion object {

    @JvmStatic
    val orderKey: DataKey<Int> = DataKey.create("Order")

    @JvmStatic
    @JvmOverloads
    fun getActions(order: Int = 0) = getActionList(order).toTypedArray()

    @JvmStatic
    @JvmOverloads
    fun getActionList(order: Int = 0): List<AnAction> {
      val actionManager = ActionManager.getInstance()
      val createAction = actionManager.getAction("CreateRunConfiguration")
      val extensions = Executor.EXECUTOR_EXTENSION_NAME.extensionList
      val result = ArrayList<AnAction>(extensions.size + (if (createAction == null) 0 else 2))
      extensions
        .mapNotNullTo(result) { executor ->
          actionManager.getAction(executor.contextActionId)?.let {
            ExecutorAction(it, executor, order)
          }
        }
      if (createAction != null) {
        result.add(createAction)
      }
      return result
    }

    private fun wrapEvent(e: AnActionEvent, order: Int): AnActionEvent {
      return if (order == 0) e
      else e.withDataContext(CustomizedDataContext.withSnapshot(e.dataContext) { sink ->
        sink[orderKey] = order
      })
    }

    @ApiStatus.Internal
    @JvmStatic
    fun wrap(runContextAction: AnAction, executor: Executor, order: Int): AnAction {
      return ExecutorAction(runContextAction, executor, order)
    }
  }

  override fun update(e: AnActionEvent) {
    origin.update(wrapEvent(e, order))
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (origin !is ActionGroup) return EMPTY_ARRAY
    if (e == null) return origin.getChildren(null)
    return origin.getChildren(wrapEvent(e, order))
  }

  override fun actionPerformed(e: AnActionEvent) {
    origin.actionPerformed(wrapEvent(e, order))
  }

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