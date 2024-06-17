// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.grave

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.RichTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ZombieCodeVisionEntry
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
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
  override fun namePrefix(): String = "persistent-code-vision"
  override fun valueExternalizer(): CodeVisionState.Externalizer = CodeVisionState.Externalizer
  override fun useHeapCache(): Boolean = true

  fun raise(file: VirtualFile, document: Document): Iterable<Pair<TextRange, CodeVisionEntry>>? {
    if (isEnabled() && file is VirtualFileWithId) {
      val state = cache[file.id]
      if (state == null) {
        LOG.debug { "code vision grave: empty state ${file.name}" }
        return null
      }
      if (state.contentHash != contentHash(document)) {
        LOG.debug { "code vision grave: state outdated ${file.name}" }
        cache.remove(file.id)
        return null
      }
      LOG.debug { "code vision grave: zombie raised ${file.name} with entries ${state.entries.size}" }
      return state.asZombies().sortedBy { (_, cvEntry) -> cvEntry.providerId }
    }
    return null
  }

  fun bury(editor: Editor, rangeMarkers: Sequence<Pair<TextRange, CodeVisionEntry>>) {
    if (isEnabled() && editor.editorKind == EditorKind.MAIN_EDITOR) {
      val file = editor.virtualFile
      if (file is VirtualFileWithId) {
        val stateList = rangeMarkers
          .filter { (_, cvEntry) -> !ignoreEntry(cvEntry) }
          .map { CodeVisionEntryState.create(it) }
          .toList()
        val state = CodeVisionState(contentHash(editor.document), stateList)
        scope.launch(Dispatchers.IO) {
          LOG.debug { "code vision grave: bury zombie ${file.name} with ${state.entries.size}" }
          cache[file.id] = state
        }
      }
    }
  }

  private fun ignoreEntry(cvEntry: CodeVisionEntry): Boolean {
    // TODO: rich text in not supported yet
    return cvEntry is ZombieCodeVisionEntry || cvEntry is RichTextCodeVisionEntry
  }

  private fun isEnabled() = Registry.`is`("cache.inlay.hints.on.disk", true)

  companion object {
    private val LOG = logger<CodeVisionGrave>()
  }
}
