// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.impl.HighlightingMarkupGrave.FileMarkupInfo
import com.intellij.openapi.fileEditor.impl.text.TextEditorCache
import com.intellij.openapi.fileEditor.impl.text.VersionedExternalizer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileWithId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.DataInput
import java.io.DataOutput

internal class HighlightingMarkupStore(project: Project, private val scope: CoroutineScope) : TextEditorCache<FileMarkupInfo>(project, scope) {
  override fun namePrefix() = "persistent-markup"
  override fun valueExternalizer() = FileMarkupInfoExternalizer
  override fun useHeapCache() = false

  fun getMarkup(file: VirtualFileWithId): FileMarkupInfo? {
    return cache[file.id]
  }

  fun putMarkup(file: VirtualFileWithId, markupInfo: FileMarkupInfo) {
    cache[file.id] = markupInfo
  }

  fun removeMarkup(file: VirtualFileWithId) {
    cache.remove(file.id)
  }

  fun executeAsync(runnable: Runnable) {
    scope.launch(Dispatchers.IO) {
      runnable.run()
    }
  }

  object FileMarkupInfoExternalizer : VersionedExternalizer<FileMarkupInfo> {
    override fun serdeVersion() = 2
    override fun save(output: DataOutput, value: FileMarkupInfo) = value.bury(output)
    override fun read(input: DataInput) = FileMarkupInfo.exhume(input)
  }
}
