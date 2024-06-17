// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.fileEditor.impl.text.TextEditorCache
import com.intellij.openapi.fileEditor.impl.text.VersionedExternalizer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileWithId
import kotlinx.coroutines.CoroutineScope
import java.io.DataInput
import java.io.DataOutput

internal class HighlightingMarkupStore(project: Project, coroutineScope: CoroutineScope) : TextEditorCache<FileMarkupInfo>(project, coroutineScope) {
  override fun namePrefix(): String = "persistent-markup"

  override fun valueExternalizer(): VersionedExternalizer<FileMarkupInfo> = FileMarkupInfoExternalizer

  override fun useHeapCache(): Boolean = false

  fun getMarkup(file: VirtualFileWithId): FileMarkupInfo? = cache[file.id]

  fun putMarkup(file: VirtualFileWithId, markupInfo: FileMarkupInfo) {
    cache[file.id] = markupInfo
  }

  fun removeMarkup(file: VirtualFileWithId) {
    cache.remove(file.id)
  }
}

private object FileMarkupInfoExternalizer : VersionedExternalizer<FileMarkupInfo> {
  override fun serdeVersion(): Int = 3
  override fun save(output: DataOutput, value: FileMarkupInfo): Unit = value.bury(output)
  override fun read(input: DataInput): FileMarkupInfo = FileMarkupInfo.readFileMarkupInfo(input)
}
