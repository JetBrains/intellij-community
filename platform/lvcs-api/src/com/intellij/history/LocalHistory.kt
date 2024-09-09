// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Provides the functionality for reporting IDE activity to the local history.
 *
 * Actions allow to group file changes together and show them as a single item in the local history list.
 * Example:
 * ```
 * val action = LocalHistory.getInstance().startAction(activityName, activityId)
 * try {
 *   // make changes to the files or refresh VFS to get external changes
 *   // these changes are going to be displayed under the provided "activityName"
 * }
 * finally {
 *   action.finish()
 * }
 * ```
 *
 * Labels allow marking specific points in the local history of a project:
 * * Event labels mark IDE events which are expected to be visible by the user (for example, commit).
 * * User labels are explicitly set by the users.
 * * System labels are used internally, for example, for performing "undo" or comparing different project states.
 *
 * Actions and event labels can store an [ActivityId] -- object that allows to identify the source of the action or event
 * and to customize its appearance in the local history list.
 * NB: this functionality is only supported in the experimental "Activity" tool window (see "lvcs.show.activity.view" registry key).
 *
 * @see [LocalHistoryAction]
 * @see [Label]
 * @see [ActivityId]
 */
abstract class LocalHistory {

  /**
   * Is Local History enabled for all projects.
   */
  abstract val isEnabled: Boolean

  /**
   * Starts an action in the local history with the given name and [ActivityId] to indicate the start of file changes.
   * Call [LocalHistoryAction.finish] after the changes were performed.
   *
   * @param name the name of the action
   * @param activityId the [ActivityId] associated with the action
   * @return the [LocalHistoryAction] object representing the started action
   */
  abstract fun startAction(name: @NlsContexts.Label String?, activityId: ActivityId?): LocalHistoryAction

  /**
   * Starts an action in the local history with the given name and [ActivityId] to indicate the start of file changes.
   * Call [LocalHistoryAction.finish] after the changes were performed.
   *
   * @param name the name of the action
   * @return the [LocalHistoryAction] object representing the started action
   */
  fun startAction(name: @NlsContexts.Label String?): LocalHistoryAction = startAction(name, null)

  /**
   * Puts a label for an IDE event in the local history.
   *
   * @param project the project where the event occurred
   * @param name the name of the event
   * @param activityId the [ActivityId] associated with the event
   * @return the Label object representing the event label
   */
  abstract fun putEventLabel(project: Project, name: @NlsContexts.Label String, activityId: ActivityId): Label

  /**
   * Puts a system label to mark a certain point in local history.
   * Note that system labels added inside the [LocalHistoryAction] are not visible,
   * also they are not visible in the experimental "Activity" tool window.
   *
   * @param project the project where the event occurred
   * @param name the name of the label
   * @param color color of the label as returned by [java.awt.Color.getRGB]
   * @return the [Label] object representing the event label
   */
  abstract fun putSystemLabel(project: Project, name: @NlsContexts.Label String, color: Int): Label

  /**
   * Puts a system label to mark a certain point in local history.
   * Note that system labels added inside the [LocalHistoryAction] are not visible,
   * also they are not visible in the experimental "Activity" tool window.
   *
   * @param project the project where the event occurred
   * @param name the name of the label
   * @return the [Label] object representing the event label
   */
  fun putSystemLabel(project: Project, name: @NlsContexts.Label String): Label = putSystemLabel(project, name, -1)

  /**
   * Puts a user label in the local history for the given project.
   * User labels are explicitly set by the users.
   *
   * @param project the project for the label
   * @param name the name of the label
   * @return the [Label] object representing the user label
   */
  @ApiStatus.Internal
  abstract fun putUserLabel(project: Project, name: @NlsContexts.Label String): Label

  /**
   * Retrieves the byte content of a given file from the local history, based on a provided condition.
   *
   * @param file the virtual file for which to retrieve the byte content
   * @param condition the condition to check for each file revision's timestamp.
   *                  The most recent revision that satisfies this condition will be used.
   * @return the byte array representing the content of the file, or null if local history is not initialized,
   *         file is not tracked in the local history, no matching entry is found or content in the matching entry is not available
   */
  abstract fun getByteContent(file: VirtualFile, condition: FileRevisionTimestampComparator): ByteArray?

  /**
   * Checks if the local history is initialized, and the given file is versioned in the local history.
   *
   * @param file the virtual file to be checked
   * @return true if the local history is initialized and the file is versioned, false otherwise
   */
  abstract fun isUnderControl(file: VirtualFile): Boolean

  private class Dummy : LocalHistory() {
    override val isEnabled: Boolean = true

    override fun startAction(name: @NlsContexts.Label String?, activityId: ActivityId?): LocalHistoryAction = LocalHistoryAction.NULL
    override fun putEventLabel(project: Project, name: String, activityId: ActivityId): Label = Label.NULL_INSTANCE
    override fun putSystemLabel(project: Project, name: @NlsContexts.Label String, color: Int): Label = Label.NULL_INSTANCE
    override fun putUserLabel(project: Project, name: @NlsContexts.Label String): Label = Label.NULL_INSTANCE
    override fun getByteContent(file: VirtualFile, condition: FileRevisionTimestampComparator): ByteArray? = null
    override fun isUnderControl(file: VirtualFile): Boolean = false
  }

  companion object {
    @JvmField
    val VFS_EVENT_REQUESTOR: Any = Any()

    @JvmStatic
    fun getInstance(): LocalHistory {
      return ApplicationManager.getApplication().getService(LocalHistory::class.java) ?: Dummy()
    }
  }
}
