// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon

@TestApplication
internal class ParallelTextEditorInitializationTest {
  private val project = projectFixture()

  @Test
  @SystemProperty(propertyKey = "idea.editor.highlighter.prepare.in.parallel", propertyValue = "true")
  fun `factory listeners receive prepared highlighting for the current document`(@TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking {
      val testProject = project.get()
      val highlighter = BlockingHighlighter()
      FileTypeEditorHighlighterProviders.getInstance().addExplicitExtension(
        fileType, EditorHighlighterProvider { _, _, _, _ -> highlighter }, disposable,
      )
      val factory = EditorFactory.getInstance()
      val document = factory.createDocument("before")
      var created = 0
      factory.addEditorFactoryListener(object : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
          if (event.editor.document !== document) return
          assertTrue(highlighter.preloaded.get())
          val editor = event.editor as EditorEx
          assertSame(highlighter, editor.highlighter)
          assertEquals(document.textLength, editor.highlighter.createIterator(0).end)
          created++
        }
      }, disposable)
      val editorScope = childScope("parallel editor test", Dispatchers.Default)
      val creation = async(Dispatchers.Default) {
        PsiAwareTextEditorProvider().createFileEditor(
          testProject, LightVirtualFile("current.parallel", fileType, "before"), document, editorScope,
        )
      }
      var textEditor: TextEditor? = null
      try {
        highlighter.started.await()
        withContext(Dispatchers.EDT) {
          assertEquals(0, created)
          assertTrue(factory.getEditors(document).isEmpty())
          WriteAction.run<RuntimeException> { document.setText("after the change") }
        }
        highlighter.proceed.countDown()
        val opened = creation.await()
        textEditor = opened
        withContext(Dispatchers.EDT) {
          assertEquals(1, created)
          val editor = opened.editor as EditorEx
          assertSame(highlighter, editor.highlighter)
          assertEquals(document.textLength, editor.highlighter.createIterator(0).end)
        }
      }
      finally {
        highlighter.proceed.countDown()
        creation.cancelAndJoin()
        withContext(Dispatchers.EDT) { textEditor?.let(Disposer::dispose) }
        editorScope.cancel()
        editorScope.coroutineContext.job.join()
      }
    }

  @Test
  @SystemProperty(propertyKey = "idea.editor.highlighter.prepare.in.parallel", propertyValue = "true")
  fun `cancel an open while highlighting is being prepared`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val testProject = project.get()
    val highlighter = BlockingHighlighter()
    FileTypeEditorHighlighterProviders.getInstance().addExplicitExtension(
      fileType, EditorHighlighterProvider { _, _, _, _ -> highlighter }, disposable,
    )
    val factory = EditorFactory.getInstance()
    val document = factory.createDocument("text")
    val editorScope = childScope("cancelled parallel editor test", Dispatchers.Default)
    val creation = async(Dispatchers.Default) {
      PsiAwareTextEditorProvider().createFileEditor(
        testProject, LightVirtualFile("cancel.parallel", fileType, "text"), document, editorScope,
      )
    }
    try {
      highlighter.started.await()
      withContext(Dispatchers.EDT) { assertTrue(factory.getEditors(document).isEmpty()) }
      creation.cancel()
      highlighter.proceed.countDown()
      creation.join()
      assertTrue(creation.isCancelled)
      withContext(Dispatchers.EDT) { assertTrue(factory.getEditors(document).isEmpty()) }
    }
    finally {
      highlighter.proceed.countDown()
      creation.cancelAndJoin()
      editorScope.cancel()
      editorScope.coroutineContext.job.join()
    }
  }

  private class BlockingHighlighter : EmptyEditorHighlighter() {
    val started = CompletableDeferred<Unit>()
    val proceed = CountDownLatch(1)
    val preloaded = AtomicBoolean()
    private val first = AtomicBoolean(true)

    override fun setText(text: CharSequence) {
      if (first.compareAndSet(true, false)) {
        started.complete(Unit)
        check(proceed.await(10, TimeUnit.SECONDS))
      }
      super.setText(text)
    }

    override fun createIterator(startOffset: Int): HighlighterIterator {
      val iterator = super.createIterator(startOffset)
      return object : HighlighterIterator by iterator {
        override fun getTextAttributes(): TextAttributes? {
          preloaded.set(true)
          return iterator.textAttributes
        }
      }
    }
  }

  private companion object {
    val fileType = ParallelEditorFileType
  }
}

private object ParallelEditorLanguage : Language("ParallelTextEditorInitializationTest")

private object ParallelEditorFileType : LanguageFileType(ParallelEditorLanguage) {
  override fun getName(): String = "Parallel editor test"
  override fun getDescription(): String = "Parallel editor test"
  override fun getDefaultExtension(): String = "parallel"
  override fun getIcon(): Icon? = null
}
