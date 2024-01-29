// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile

abstract class LocalHistory {
  abstract fun startAction(name: @NlsContexts.Label String?, activityId: ActivityId?): LocalHistoryAction

  fun startAction(name: @NlsContexts.Label String?): LocalHistoryAction = startAction(name, null)

  abstract fun putEventLabel(project: Project, name: @NlsContexts.Label String, activityId: ActivityId): Label

  abstract fun putSystemLabel(project: Project, name: @NlsContexts.Label String, color: Int = -1): Label

  fun putSystemLabel(project: Project, name: @NlsContexts.Label String): Label = putSystemLabel(project, name, -1)

  abstract fun putUserLabel(project: Project, name: @NlsContexts.Label String): Label?

  abstract fun getByteContent(file: VirtualFile, condition: FileRevisionTimestampComparator): ByteArray?

  abstract fun isUnderControl(file: VirtualFile): Boolean

  private class Dummy : LocalHistory() {
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
