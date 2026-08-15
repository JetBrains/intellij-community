// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.fileEditor.impl.text

import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import kotlin.coroutines.CoroutineContext

class PsiAwareTextEditorProviderTest : LightPlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  fun testSyntaxHighlighterPreloadDoesNotOverlapEditorInitialization() = timeoutRunBlocking {
    val preloaded = AtomicBoolean()
    val highlighter = object : EmptyEditorHighlighter() {
      override fun createIterator(startOffset: Int): HighlighterIterator {
        val delegate = super.createIterator(startOffset)
        return object : HighlighterIterator by delegate {
          override fun getTextAttributes(): TextAttributes? {
            preloaded.set(true)
            return delegate.textAttributes
          }
        }
      }
    }
    val highlighterProvider = EditorHighlighterProvider { _, _, _, _ -> highlighter }
    FileTypeEditorHighlighterProviders.getInstance().addExplicitExtension(TEST_FILE_TYPE, highlighterProvider, testRootDisposable)

    val file = LightVirtualFile("preload.test", TEST_FILE_TYPE, "text")
    val editorScope = CoroutineScope(SupervisorJob() + QueuingDispatcher())
    try {
      val exception = try {
        TestPsiAwareTextEditorProvider(preloaded).createFileEditor(
          project = project,
          file = file,
          document = EditorFactory.getInstance().createDocument("text"),
          editorCoroutineScope = editorScope,
        )
        throw AssertionError("Editor initialization was not observed")
      }
      catch (exception: EditorInitializationReachedException) {
        exception
      }

      assertTrue("The syntax highlighter must be preloaded before editor initialization", exception.preloaded)
    }
    finally {
      editorScope.cancel()
    }
  }
}

private class TestPsiAwareTextEditorProvider(
  private val preloaded: AtomicBoolean,
) : PsiAwareTextEditorProvider() {
  override fun initializeEditor(
    factory: EditorFactoryImpl,
    effectiveDocument: Document,
    project: Project,
    file: VirtualFile,
    highlighter: EditorHighlighter,
    asyncLoader: AsyncEditorLoader,
  ): EditorImpl {
    throw EditorInitializationReachedException(preloaded.get())
  }
}

private class EditorInitializationReachedException(val preloaded: Boolean) : RuntimeException()

private class QueuingDispatcher : CoroutineDispatcher() {
  private val queue = ConcurrentLinkedQueue<Runnable>()

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    queue.add(block)
  }
}

private object TestLanguage : Language("PsiAwareTextEditorProviderTest")

private object TestFileType : LanguageFileType(TestLanguage) {
  override fun getName(): String = "PsiAwareTextEditorProviderTest"
  override fun getDescription(): String = "PsiAwareTextEditorProvider test file type"
  override fun getDefaultExtension(): String = "test"
  override fun getIcon(): Icon? = null
}

private val TEST_FILE_TYPE = TestFileType
