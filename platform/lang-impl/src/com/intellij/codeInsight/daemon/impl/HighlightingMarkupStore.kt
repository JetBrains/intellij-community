// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.impl.HighlightingMarkupGrave.FileMarkupInfo
import com.intellij.openapi.fileEditor.impl.text.TextEditorCache
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.io.DataExternalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.DataInput
import java.io.DataOutput

internal class HighlightingMarkupStore(project: Project, private val scope: CoroutineScope) : TextEditorCache<FileMarkupInfo>(project, scope) {
  override fun graveName() = "persistent-markup"
  override fun valueExternalizer() = FileMarkupInfoExternalizer
  override fun serdeVersion() = 2
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

  object FileMarkupInfoExternalizer : DataExternalizer<FileMarkupInfo> {
    override fun save(output: DataOutput, value: FileMarkupInfo) {
      value.bury(output)
    }

    override fun read(input: DataInput): FileMarkupInfo {
      return FileMarkupInfo.exhume(input)
    }
  }
}
