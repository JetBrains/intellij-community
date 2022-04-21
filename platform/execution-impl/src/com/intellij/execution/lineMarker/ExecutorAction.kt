// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.lineMarker

import com.intellij.execution.Executor
import com.intellij.execution.actions.RunContextAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.annotations.NonNls


/**
 * @param order corresponding sorting happens here: [com.intellij.execution.actions.BaseRunConfigurationAction.getOrderedConfiguration]
 */
@Suppress("ComponentNotRegistered")
class ExecutorAction private constructor(val origin: AnAction,
                                         val executor: Executor,
                                         val order: Int) :
  ActionGroup(), ActionWithDelegate<AnAction>, UpdateInBackground {
  init {
    copyFrom(origin)
  }

  override fun isUpdateInBackground() = UpdateInBackground.isUpdateInBackground(origin)

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
        result.add(object : EmptyAction.MyDelegatingActionGroup(createAction as ActionGroup) {
          override fun update(e: AnActionEvent) {
            super.update(wrapEvent(e, order))
          }

          override fun actionPerformed(e: AnActionEvent) {
            super.actionPerformed(wrapEvent(e, order))
          }

          override fun getChildren(e: AnActionEvent?): Array<AnAction> {
            return super.getChildren(e?.let { wrapEvent(e, order)})
          }
        })
      }
      return result
    }

    private fun wrapEvent(e: AnActionEvent, order : Int): AnActionEvent {
      val dataContext = wrapContext(e.dataContext, order)
      return AnActionEvent(e.inputEvent, dataContext, e.place, e.presentation, e.actionManager, e.modifiers)
    }

    private fun wrapContext(dataContext: DataContext, order : Int): DataContext {
      return if (order == 0) dataContext else MyDataContext(dataContext, order)
    }

    @JvmStatic
    fun wrap(runContextAction: RunContextAction, order: Int): AnAction {
      return ExecutorAction(runContextAction, runContextAction.executor, order)
    }
  }

  override fun getDelegate(): AnAction {
    return origin
  }

  override fun update(e: AnActionEvent) {
    origin.update(wrapEvent(e, order))
    if (origin !is ActionGroup) {
      e.presentation.isPerformGroup = true
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    origin.actionPerformed(wrapEvent(e, order))
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = (origin as? ActionGroup)?.getChildren(e?.let { wrapEvent(it, order) }) ?: AnAction.EMPTY_ARRAY

  override fun isDumbAware() = origin.isDumbAware

  override fun isPopup() = origin !is ActionGroup || origin.isPopup

  override fun hideIfNoVisibleChildren() = origin is ActionGroup && origin.hideIfNoVisibleChildren()

  override fun disableIfNoVisibleChildren() = origin is ActionGroup && origin.disableIfNoVisibleChildren()

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
  
  private class MyDataContext(private val myDelegate: DataContext, val order: Int) : UserDataHolderBase(), DataContext {
    @Synchronized
    override fun getData(dataId: @NonNls String): Any? {
      if (orderKey.`is`(dataId)) {
        return order
      }
      return myDelegate.getData(dataId)
    }

    override fun <T : Any?> getUserData(key: Key<T>): T? {
      if (myDelegate is UserDataHolder) {
        return myDelegate.getUserData(key)
      }
      return super.getUserData(key)
    }

    override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
      if (myDelegate is UserDataHolder) {
        myDelegate.putUserData(key, value)
      }
      else{
        super.putUserData(key, value)
      }
    }
  }
}