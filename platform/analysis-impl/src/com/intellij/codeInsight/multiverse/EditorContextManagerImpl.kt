// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.util.AtomicMapCache
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class EditorContextManagerImpl(
  private val project: Project,
  cs: CoroutineScope,
) : EditorContextManager, Disposable.Default {

  // todo IJPL-339 don't drop current contexts entirely on invalidating contexts. try to restore them on the next request
  private val currentContextCache = AtomicMapCache { CollectionFactory.createConcurrentWeakMap<Editor, EditorSelectedContexts>() }

  private val _eventFlow = MutableSharedFlow<EditorContextManager.ChangeEvent>(extraBufferCapacity = Int.MAX_VALUE)

  init {
    project.messageBus.connect(cs).subscribe(CodeInsightContextManager.topic, object : CodeInsightContextChangeListener {
      override fun contextsChanged() {
        log.info("Dropping all editor contexts")
        currentContextCache.invalidate()
      }
    })
  }

  override fun getCachedEditorContexts(editor: Editor): EditorSelectedContexts? {
    return currentContextCache[editor]
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  override fun getEditorContexts(editor: Editor): EditorSelectedContexts {
    return getCurrentContextStateWithPreferredDefault(editor)
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  private fun getCurrentContextStateWithPreferredDefault(editor: Editor): EditorSelectedContexts {
    assert(editor.project?.equals(project)?:true) {
      "called with wrong project: $project. editor project: ${editor.project}"
    }
    if (!isSharedSourceSupportEnabled(project)) {
      return SingleEditorContext(defaultContext())
    }

    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: run {
      // Do not cache the default fallback used when the document is not (yet) backed by a file.
      // The cache is only refreshed on CodeInsightContextManager.contextsChanged, while a document
      // becoming file-backed does not fire that event. Caching the fallback would therefore pin the
      // editor to DefaultContext forever, even after its real (e.g. library/module) context becomes
      // available, and later highlighting would fail the editor-vs-file context check (IJPL-240162).
      // See MultiverseHighlightingTest.testDefaultContextOfFilelessDocumentIsNotCached.
      log.trace { "editor context for $editor is set to default (uncached): document has no file" }
      return SingleEditorContext(defaultContext())
    }

    val context = currentContextCache.computeIfAbsent(editor) {
      val preferredContext = CodeInsightContextManager.getInstance(project).getPreferredContext(file)
      val contexts = SingleEditorContext(preferredContext)

      fireEvent(editor, contexts)

      log.trace { "editor context for $editor is set to $contexts" }

      contexts
    }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      ensureContextRelevant(file, context.mainContext, project)
    }

    return context
  }

  //@RequiresWriteLock
  override fun setEditorContext(editor: Editor, contexts: EditorSelectedContexts) {
    //ThreadingAssertions.assertWriteAccess()
    setEditorContextNoFire(editor, contexts)

    fireEvent(editor, contexts)
  }

  override fun setEditorContextNoFire(editor: Editor, contexts: EditorSelectedContexts) {
    currentContextCache[editor] = contexts

    log.trace { "Editor context for $editor is set to $contexts" }
  }

  override val eventFlow: Flow<EditorContextManager.ChangeEvent>
    get() = _eventFlow.asSharedFlow()

  private fun fireEvent(editor: Editor, contexts: EditorSelectedContexts) {
    val event = EditorContextManager.ChangeEvent(editor, contexts)
    project.messageBus.syncPublisher(EditorContextManager.topic).editorContextsChanged(event)
    if (!_eventFlow.tryEmit(event)) {
      log.error("Failed to emit event: $event")
    }
  }
}

private val log = logger<EditorContextManagerImpl>()