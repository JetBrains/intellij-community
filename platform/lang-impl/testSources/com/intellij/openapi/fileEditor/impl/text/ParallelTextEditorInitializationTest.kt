// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.concurrency.currentThreadContext
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.highlighter.HighlighterClient
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.impl.zombie.Necropolis
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.concurrency.SequentialTaskExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
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
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon
import kotlin.coroutines.CoroutineContext

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
      var colorSchemeUpdatesAtPublication: Int? = null
      factory.addEditorFactoryListener(object : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
          if (event.editor.document !== document) return
          assertTrue(highlighter.preloaded.get())
          colorSchemeUpdatesAtPublication = highlighter.replacementColorSchemeUpdates
          val editor = event.editor as EditorEx
          assertSame(highlighter, editor.highlighter)
          assertEquals(document.textLength, editor.highlighter.createIterator(0).end)
          created++
        }
      }, disposable)
      val editorScope = childScope("parallel editor test", Dispatchers.Default)
      val dispatcher = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("parallel editor initialization test")
        .asCoroutineDispatcher()
      val creation = async(dispatcher) {
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
          assertEquals(1, colorSchemeUpdatesAtPublication, "The pending editor must install the prepared highlighter")
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
        dispatcher.close()
      }
    }

  @Test
  @SystemProperty(propertyKey = "idea.editor.highlighter.prepare.in.parallel", propertyValue = "true")
  fun `ready highlighting avoids an editor shell`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val testProject = project.get()
    serviceAsync<EditorColorsManager>()
    serviceAsync<EditorHighlighterFactory>()
    Necropolis.getInstanceAsync(testProject)
    val highlighter = TrackingHighlighter()
    FileTypeEditorHighlighterProviders.getInstance().addExplicitExtension(
      fileType, EditorHighlighterProvider { _, _, _, _ -> highlighter }, disposable,
    )
    val factory = EditorFactory.getInstance()
    val document = factory.createDocument("text")
    var created = 0
    var colorSchemeUpdatesAtPublication: Int? = null
    factory.addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorCreated(event: EditorFactoryEvent) {
        if (event.editor.document !== document) return
        assertSame(highlighter, (event.editor as EditorEx).highlighter)
        colorSchemeUpdatesAtPublication = highlighter.replacementColorSchemeUpdates
        created++
      }
    }, disposable)
    val editorScope = childScope("ready highlighter editor test", Dispatchers.Default)
    val dispatcher = CompleteChildrenDispatcher()
    var textEditor: TextEditor? = null
    try {
      textEditor = withContext(dispatcher) {
        PsiAwareTextEditorProvider().createFileEditor(
          testProject, LightVirtualFile("ready.parallel", fileType, "text"), document, editorScope,
        )
      }
      assertEquals(1, created)
      assertEquals(0, colorSchemeUpdatesAtPublication, "The editor must install the highlighter during construction")
      assertEquals(1, dispatcher.resumptionsFromEdt.get(), "The ready highlighter must need one EDT block")
    }
    finally {
      withContext(Dispatchers.EDT) { textEditor?.let(Disposer::dispose) }
      editorScope.cancel()
      editorScope.coroutineContext.job.join()
    }
  }

  @Test
  @SystemProperty(propertyKey = "idea.editor.highlighter.prepare.in.parallel", propertyValue = "true")
  fun `highlighting ready before EDT execution avoids an editor shell`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val testProject = project.get()
    serviceAsync<EditorColorsManager>()
    serviceAsync<EditorHighlighterFactory>()
    Necropolis.getInstanceAsync(testProject)
    val highlighter = BlockingHighlighter()
    FileTypeEditorHighlighterProviders.getInstance().addExplicitExtension(
      fileType, EditorHighlighterProvider { _, _, _, _ -> highlighter }, disposable,
    )
    val factory = EditorFactory.getInstance()
    val document = factory.createDocument("text")
    var created = 0
    var colorSchemeUpdatesAtPublication: Int? = null
    factory.addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorCreated(event: EditorFactoryEvent) {
        if (event.editor.document !== document) return
        assertSame(highlighter, (event.editor as EditorEx).highlighter)
        colorSchemeUpdatesAtPublication = highlighter.replacementColorSchemeUpdates
        created++
      }
    }, disposable)
    val editorScope = childScope("queued highlighter editor test", Dispatchers.Default)
    val dispatcher = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("queued editor initialization test")
      .asCoroutineDispatcher()
    val edtEntered = CompletableDeferred<Unit>()
    val edtProceed = CountDownLatch(1)
    val edtGate = async(Dispatchers.UI) {
      edtEntered.complete(Unit)
      check(edtProceed.await(10, TimeUnit.SECONDS))
    }
    var textEditor: TextEditor? = null
    var creation: Deferred<TextEditor>? = null
    try {
      edtEntered.await()
      val open = async(dispatcher) {
        PsiAwareTextEditorProvider().createFileEditor(
          testProject, LightVirtualFile("queued.parallel", fileType, "text"), document, editorScope,
        )
      }
      creation = open
      highlighter.started.await()
      highlighter.proceed.countDown()
      highlighter.completed.await()
      edtProceed.countDown()
      edtGate.await()
      textEditor = open.await()
      assertEquals(1, created)
      assertEquals(0, colorSchemeUpdatesAtPublication, "The editor must install the highlighter during construction")
    }
    finally {
      highlighter.proceed.countDown()
      edtProceed.countDown()
      edtGate.cancelAndJoin()
      creation?.cancelAndJoin()
      withContext(Dispatchers.EDT) { textEditor?.let(Disposer::dispose) }
      editorScope.cancel()
      editorScope.coroutineContext.job.join()
      dispatcher.close()
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

  private open class TrackingHighlighter : EmptyEditorHighlighter() {
    var replacementColorSchemeUpdates = 0
      private set
    private var replacedHighlighter = false

    override fun setEditor(editor: HighlighterClient) {
      super.setEditor(editor)
      val concreteEditor = editor as EditorEx
      val listenerDisposable = Disposer.newDisposable()
      EditorUtil.disposeWithEditor(concreteEditor, listenerDisposable)
      concreteEditor.addPropertyChangeListener({ event ->
        if (event.propertyName == EditorEx.PROP_HIGHLIGHTER) {
          replacedHighlighter = event.oldValue != null
        }
      }, listenerDisposable)
    }

    override fun setColorScheme(scheme: EditorColorsScheme) {
      super.setColorScheme(scheme)
      if (replacedHighlighter) replacementColorSchemeUpdates++
    }
  }

  private class BlockingHighlighter : TrackingHighlighter() {
    val started = CompletableDeferred<Unit>()
    val completed = CompletableDeferred<Unit>()
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
          if (preloaded.compareAndSet(false, true)) {
            val job = currentThreadContext().job
            check(job is Deferred<*>)
            job.invokeOnCompletion { completed.complete(Unit) }
          }
          return iterator.textAttributes
        }
      }
    }
  }

  private class CompleteChildrenDispatcher : CoroutineDispatcher() {
    val resumptionsFromEdt = AtomicInteger()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      val job = context[Job] as? Deferred<*>
      if (job == null) {
        if (ApplicationManager.getApplication().isDispatchThread) resumptionsFromEdt.incrementAndGet()
        Dispatchers.Default.dispatch(context, block)
        return
      }
      val completed = CountDownLatch(1)
      job.invokeOnCompletion { completed.countDown() }
      block.run()
      check(completed.await(10, TimeUnit.SECONDS))
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
