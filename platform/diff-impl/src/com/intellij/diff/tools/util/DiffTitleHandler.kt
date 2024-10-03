// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util

import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.tools.util.side.OnesideContentPanel
import com.intellij.diff.tools.util.side.ThreesideContentPanel
import com.intellij.diff.tools.util.side.TwosideContentPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
abstract class DiffTitleHandler private constructor(
  disposable: Disposable,
  private val modalityComponent: JComponent
) {

  private val updateAlarm = Alarm(disposable)

  fun scheduleUpdate() {
    updateAlarm.cancelAllRequests()
    updateAlarm.addRequest({ updateTitles() }, 300, ModalityState.stateForComponent(modalityComponent))
  }

  abstract fun updateTitles()


  private fun installListener(contents: List<DiffContent>, disposable: Disposable) {
    updateTitles()

    val files = contents.mapNotNull { content ->
      when (content) {
        is FileContent -> content.file
        else -> null
      }
    }
    if (!files.isEmpty()) {
      ApplicationManager.getApplication().messageBus.connect(disposable)
        .subscribe(VirtualFileManager.VFS_CHANGES, MyBulkFileListener(files))
    }
  }

  private inner class MyBulkFileListener(private val files: List<VirtualFile>) : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
      var shouldRefresh = false

      for (event in events) {
        val file = event.file
        if (file != null && files.contains(file)) {
          shouldRefresh = true
          break
        }
      }

      if (shouldRefresh) {
        scheduleUpdate()
      }
    }
  }


  private class OnesideDiffTitleHandler(private val provider: DiffTitleProvider,
                                        disposable: Disposable,
                                        private val panel: OnesideContentPanel) : DiffTitleHandler(disposable, panel) {
    override fun updateTitles() {
      panel.setTitle(provider.createTitle())
    }
  }

  private class TwosideDiffTitleHandler(private val provider: DiffTitlesProvider,
                                        disposable: Disposable,
                                        private val panel: TwosideContentPanel) : DiffTitleHandler(disposable, panel) {
    override fun updateTitles() {
      panel.setTitles(provider.createTitles())
    }
  }

  private class ThreesideDiffTitleHandler(private val provider: DiffTitlesProvider,
                                          disposable: Disposable,
                                          private val panel: ThreesideContentPanel) : DiffTitleHandler(disposable, panel) {
    override fun updateTitles() {
      panel.setTitles(provider.createTitles())
    }
  }

  interface DiffTitleProvider {
    fun createTitle(): JComponent?
  }

  interface DiffTitlesProvider {
    fun createTitles(): List<JComponent?>
  }

  companion object {
    @JvmStatic
    fun createHandler(provider: DiffTitleProvider,
                      panel: OnesideContentPanel,
                      request: ContentDiffRequest,
                      disposable: Disposable): DiffTitleHandler {
      val handler: DiffTitleHandler = OnesideDiffTitleHandler(provider, disposable, panel)
      handler.installListener(request.contents, disposable)
      return handler
    }

    @JvmStatic
    fun createHandler(provider: DiffTitlesProvider,
                      panel: TwosideContentPanel,
                      request: ContentDiffRequest,
                      disposable: Disposable): DiffTitleHandler {
      val handler: DiffTitleHandler = TwosideDiffTitleHandler(provider, disposable, panel)
      handler.installListener(request.contents, disposable)
      return handler
    }


    @JvmStatic
    fun createHandler(provider: DiffTitlesProvider,
                      panel: ThreesideContentPanel,
                      request: ContentDiffRequest,
                      disposable: Disposable): DiffTitleHandler {
      val handler: DiffTitleHandler = ThreesideDiffTitleHandler(provider, disposable, panel)
      handler.installListener(request.contents, disposable)
      return handler
    }
  }
}
