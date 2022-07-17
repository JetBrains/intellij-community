// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.activity

import com.intellij.icons.AllIcons.Actions.Run_anything
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject
import com.intellij.ide.actions.runAnything.activity.RunAnythingNotifiableProvider.ExecutionStatus.ERROR
import com.intellij.ide.actions.runAnything.activity.RunAnythingNotifiableProvider.ExecutionStatus.SUCCESS
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.NlsActions

/**
 * Implement notifiable provider if you desire to run an arbitrary activity in the IDE, that may hasn't provide visual effects,
 * and show notification about it with optional actions.
 *
 * @param V see [RunAnythingProvider]
 */
abstract class RunAnythingNotifiableProvider<V> : RunAnythingProviderBase<V>() {

  private val runAnythingGroup = NotificationGroupManager.getInstance().getNotificationGroup("Run Anything")

  private val notificationConfigurators = LinkedHashMap<ExecutionStatus, NotificationBuilder.() -> Unit>()

  /**
   * Runs an activity silently.
   *
   * @param dataContext 'Run Anything' data context
   * @return true if succeed, false is failed
   */
  protected abstract fun run(dataContext: DataContext, value: V): Boolean

  override fun execute(dataContext: DataContext, value: V) {
    try {
      when (run(dataContext, value)) {
        true -> notifyNotificationIfNeeded(SUCCESS, dataContext, value)
        else -> notifyNotificationIfNeeded(ERROR, dataContext, value)
      }
    }
    catch (ex: Throwable) {
      notifyNotificationIfNeeded(ERROR, dataContext, value)
      throw ex
    }
  }

  private fun notifyNotificationIfNeeded(status: ExecutionStatus, dataContext: DataContext, value: V) {
    val configure = notificationConfigurators[status] ?: return
    val builder = NotificationBuilder(dataContext, value)
    val notification = builder.apply(configure).build()
    val project = fetchProject(dataContext)
    notification.notify(project)
  }

  protected fun notification(after: ExecutionStatus = SUCCESS, configure: NotificationBuilder.() -> Unit) {
    notificationConfigurators[after] = configure
  }

  protected inner class NotificationBuilder(val dataContext: DataContext, val value: V) {
    private val actions = ArrayList<ActionData>()

    var title: String? = null
    var subtitle: String? = null
    lateinit var content: String

    fun action(@NlsActions.ActionText name: String, perform: () -> Unit) {
      actions.add(ActionData(name, perform))
    }

    fun build(): Notification {
      val notification = runAnythingGroup.createNotification(content, INFORMATION).setIcon(Run_anything).setTitle(title, subtitle)
      for (actionData in actions) {
        val action = object : AnAction(actionData.name) {
          override fun actionPerformed(e: AnActionEvent) {
            actionData.perform()
            notification.expire()
          }
        }
        notification.addAction(action)
      }
      return notification
    }
  }

  protected enum class ExecutionStatus { SUCCESS, ERROR }

  private data class ActionData(@NlsActions.ActionText val name: String, val perform: () -> Unit)

  init {
    notification(ERROR) {
      title = IdeBundle.message("run.anything.notification.warning.title")
      content = IdeBundle.message("run.anything.notification.warning.content", getCommand(value))
    }
  }
}
