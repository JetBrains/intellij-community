// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus

internal class AnalysisIgnoreVfsListener : AsyncFileListener {

  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    val changes = collectAnalysisIgnoreVfsChanges(events) ?: return null

    return object : AsyncFileListener.ChangeApplier {
      override fun afterVfsChange() {
        for (project in ProjectManager.getInstance().openProjects) {
          changes.applyTo(AnalysisIgnoreService.getInstance(project))
        }
      }
    }
  }
}

@ApiStatus.Internal
fun collectAnalysisIgnoreVfsChanges(events: List<VFileEvent>): AnalysisIgnoreVfsChanges? {
  val changes = AnalysisIgnoreVfsChanges()
  for (event in events) {
    when (event) {
      is VFileCreateEvent -> {
        if (event.childName == ANALYSIS_IGNORE_FILE_NAME) changes.readEvents.add(event)
      }
      is VFileCopyEvent -> {
        if (event.newChildName == ANALYSIS_IGNORE_FILE_NAME) changes.readEvents.add(event)
      }
      is VFilePropertyChangeEvent -> {
        if (event.propertyName == VirtualFile.PROP_NAME) {
          if (event.oldValue == ANALYSIS_IGNORE_FILE_NAME) event.file.parent?.let { changes.forgetBaseDirUrls.add(it.url) }
          if (event.newValue == ANALYSIS_IGNORE_FILE_NAME) changes.readEvents.add(event)
        }
      }
      is VFileContentChangeEvent -> {
        if (!event.isFromSave && event.file.isAnalysisIgnoreFile()) changes.readEvents.add(event)
      }
      is VFileDeleteEvent -> {
        changes.forgetRemoved(event.file)
      }
      is VFileMoveEvent -> {
        changes.forgetRemoved(event.file)
        if (event.file.isAnalysisIgnoreFile()) changes.readEvents.add(event)
      }
    }
  }
  return if (changes.isEmpty()) null else changes
}

@ApiStatus.Internal
class AnalysisIgnoreVfsChanges {
  val forgetBaseDirUrls: MutableList<String> = SmartList()
  val forgetSubtreeUrls: MutableList<String> = SmartList()
  val readEvents: MutableList<VFileEvent> = SmartList()

  fun isEmpty(): Boolean = forgetBaseDirUrls.isEmpty() && forgetSubtreeUrls.isEmpty() && readEvents.isEmpty()

  fun applyTo(service: AnalysisIgnoreService) {
    service.scheduleChanges(
      files = readEvents.mapNotNull { it.file },
      baseDirUrls = forgetBaseDirUrls,
      subtreeUrls = forgetSubtreeUrls,
    )
  }

  internal fun forgetRemoved(file: VirtualFile) {
    if (file.isDirectory) {
      forgetSubtreeUrls.add(file.url)
    }
    else if (file.isAnalysisIgnoreFile()) {
      file.parent?.let { forgetBaseDirUrls.add(it.url) }
    }
  }
}
