// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.preview

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.ContainerUtil.createWeakSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import java.awt.MouseInfo
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
internal class ImageOrColorPreviewService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {

  private val previewRequests = MutableSharedFlow<PreviewRequest>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  /**
   * this collection should not keep strong references to the elements
   * [getPsiElementsAt]
   */
  private var myElements = AtomicReference<Collection<PsiElement?>?>()

  init {
    coroutineScope.launch(CoroutineName("ImageOrColorPreviewService requests collector")) {
      previewRequests.debounce(100.milliseconds)
        .collectLatest {
          it.run()
        }
    }
    coroutineScope.awaitCancellationAndInvoke {
      myElements.set(null)
    }
  }

  fun attach(editor: Editor) {
    if (editor.isOneLineMode()) {
      return
    }
    val project = editor.getProject()
    if (project != this.project) {
      LOG.error("ImageOrColorPreviewService.attach: invalid project, ours is ${this.project} and the editor's is $project")
    }
    coroutineScope.launch(CoroutineName("ImageOrColorPreviewService attach job for $editor")) {
      doAttach(editor)
    }
  }

  fun detach(editor: Editor) {
    val project = editor.getProject()
    if (project != this.project) {
      LOG.error("ImageOrColorPreviewService.detach: invalid project, ours is ${this.project} and the editor's is $project")
    }
    doDetach(editor)
  }

  private suspend fun doAttach(editor: Editor) {
    val psiFile = readAction {
      if (project.isDisposed || editor.isDisposed)
        return@readAction null
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument())
      if (psiFile == null || !psiFile.isValid || psiFile is PsiCompiledElement || !isSupportedFile(psiFile)) {
        null
      }
      else {
        psiFile
      }
    }
    if (psiFile == null) return
    withContext(Dispatchers.EDT) {
      val mouseListener = MyMouseMotionListener()
      editor.addEditorMouseMotionListener(mouseListener)
      EDITOR_MOUSE_LISTENER_ADDED[editor] = mouseListener
      val keyListener: KeyListener = MyKeyListener(editor)
      editor.getContentComponent().addKeyListener(keyListener)
      EDITOR_KEY_LISTENER_ADDED[editor] = keyListener
    }
  }

  private fun doDetach(editor: Editor) {
    val keyListener = EDITOR_KEY_LISTENER_ADDED[editor]
    if (keyListener != null) {
      EDITOR_KEY_LISTENER_ADDED[editor] = null
      editor.getContentComponent().removeKeyListener(keyListener)
    }
    val mouseListener = EDITOR_MOUSE_LISTENER_ADDED[editor]
    if (mouseListener != null) {
      EDITOR_MOUSE_LISTENER_ADDED[editor] = null
      editor.removeEditorMouseMotionListener(mouseListener)
    }
  }

  inner class MyKeyListener(private val editor: Editor) : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) {
      if (e.keyCode == KeyEvent.VK_SHIFT && !editor.isOneLineMode()) {
        val pointerInfo = MouseInfo.getPointerInfo()
        if (pointerInfo != null) {
          val location = pointerInfo.location
          SwingUtilities.convertPointFromScreen(location, editor.getContentComponent())
          val offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(location))
          previewRequests.tryEmit(ShowPreviewRequest(editor, offset, true))
        }
      }
    }
  }

  private inner class MyMouseMotionListener : EditorMouseMotionListener {
    override fun mouseMoved(event: EditorMouseEvent) {
      val editor = event.editor
      if (editor.isOneLineMode()) {
        return
      }
      val elements = myElements.get()
      if (elements == null && event.mouseEvent.isShiftDown) {
        previewRequests.tryEmit(ShowPreviewRequest(editor, event.offset, false))
      }
      else if (elements != null) {
        previewRequests.tryEmit(HidePreviewRequest(editor, event.offset))
      }
    }
  }

  private abstract inner class PreviewRequest(protected val editor: Editor) {
    abstract suspend fun run()
  }

  private inner class ShowPreviewRequest(editor: Editor, private val offset: Int, private val keyTriggered: Boolean) : PreviewRequest(editor) {
    override suspend fun run() {
      val elements = readAction { getPsiElementsAt(editor, offset) }
      if (elements == myElements.get()) return
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          showElements(elements)
        }
      }
    }

    private fun showElements(elements: Collection<PsiElement>) {
      for (element in elements) {
        if (!element.isValid()) {
          return
        }
        if (
          PsiDocumentManager.getInstance(element.getProject()).isUncommited(editor.getDocument()) ||
          DumbService.getInstance(element.getProject()).isDumb
        ) {
          return
        }
        for (provider in ElementPreviewProvider.EP_NAME.extensions) {
          if (!provider.isSupportedFile(element.getContainingFile())) {
            continue
          }
          try {
            provider.show(element, editor, editor.offsetToXY(offset), keyTriggered)
          }
          catch (e: ProcessCanceledException) {
            throw e
          }
          catch (e: Exception) {
            LOG.error(e)
          }
        }
      }
      myElements.set(elements)
    }
  }

  private inner class HidePreviewRequest(editor: Editor, private val offset: Int) : PreviewRequest(editor) {
    override suspend fun run() {
      val elements = myElements.get() ?: return
      if (readAction { getPsiElementsAt (editor, offset) == elements }) {
        return // mouse moved, but not far away enough to make currently shown elements outdated
      }
      myElements.set(null)
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          hideElements(elements)
        }
      }
    }

    private fun hideElements(elements: Collection<PsiElement?>) {
      for (provider in ElementPreviewProvider.EP_NAME.extensionList) {
        try {
          for (element in elements) {
            provider.hide(element, editor)
          }
        }
        catch (e: Exception) {
          LOG.error(e)
        }
      }
    }
  }

  companion object {

    private val LOG = Logger.getInstance(ImageOrColorPreviewService::class.java)

    private val EDITOR_KEY_LISTENER_ADDED = Key.create<KeyListener>("previewManagerKeyListenerAdded")
    private val EDITOR_MOUSE_LISTENER_ADDED = Key.create<EditorMouseMotionListener>("previewManagerMouseListenerAdded")

    private fun isSupportedFile(psiFile: PsiFile): Boolean {
      for (file in psiFile.getViewProvider().getAllFiles()) {
        for (provider in ElementPreviewProvider.EP_NAME.extensionList) {
          if (provider.isSupportedFile(file)) {
            return true
          }
        }
      }
      return false
    }

    private fun getPsiElementsAt(editor: Editor, offset: Int): Collection<PsiElement> {
      if (editor.isDisposed()) {
        return emptySet()
      }
      val project = editor.getProject()
      if (project == null || project.isDisposed()) {
        return emptySet()
      }
      val documentManager = PsiDocumentManager.getInstance(project)
      val document = editor.getDocument()
      val psiFile = documentManager.getPsiFile(document)
      if (psiFile == null || psiFile is PsiCompiledElement || !psiFile.isValid()) {
        return emptySet()
      }
      val elements = createWeakSet<PsiElement>()
      if (documentManager.isCommitted(document)) {
        ContainerUtil.addIfNotNull(elements, InjectedLanguageUtil.findElementAtNoCommit(psiFile, offset))
      }
      for (file in psiFile.getViewProvider().getAllFiles()) {
        ContainerUtil.addIfNotNull(elements, file.findElementAt(offset))
      }
      return elements
    }
  }
}
