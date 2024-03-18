// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.grave

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.RichTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ZombieCodeVisionEntry
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.impl.text.TextEditorCache
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Level.PROJECT)
internal class CodeVisionGrave(project: Project, private val scope: CoroutineScope) : TextEditorCache<CodeVisionState>(project, scope) {
  override fun namePrefix() = "persistent-code-vision"
  override fun valueExternalizer() = CodeVisionState.Externalizer
  override fun useHeapCache() = true

  fun raise(file: VirtualFile, document: Document): Iterable<Pair<TextRange, CodeVisionEntry>>? {
    if (!isEnabled() || file !is VirtualFileWithId) {
      return null
    }
    val state = cache[file.id]
    if (state == null) {
      return null
    }
    if (state.contentHash != document.contentHash()) {
      cache.remove(file.id)
      return null
    }
    return state.asZombies().sortedBy { (_, entry) -> entry.providerId }
  }

  fun bury(editor: Editor, rangeMarkers: Sequence<Pair<TextRange, CodeVisionEntry>>) {
    if (!isEnabled() || editor.editorKind != EditorKind.MAIN_EDITOR) {
      return
    }
    val vFile = editor.virtualFile
    if (vFile !is VirtualFileWithId) {
      return
    }
    val stateList = rangeMarkers
      .filter { (_, cvEntry) -> !ignoreEntry(cvEntry) }
      .map { CodeVisionEntryState.create(it) }
      .toList()
    if (stateList.isEmpty()) {
      return
    }
    val contentHash = editor.document.contentHash()
    val state = CodeVisionState(contentHash, stateList)
    scope.launch(Dispatchers.IO) {
      cache[vFile.id] = state
    }
  }

  private fun ignoreEntry(cvEntry: CodeVisionEntry): Boolean {
    // TODO: rich text in not supported yet
    return cvEntry is ZombieCodeVisionEntry || cvEntry is RichTextCodeVisionEntry
  }

  private fun isEnabled() = Registry.`is`("cache.inlay.hints.on.disk")
}
