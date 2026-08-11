// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.awaitCancellation
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal suspend fun Editor.scrollToCursorPositionOnTyping(): Nothing {
  useDisposable { disposable ->
    document.addDocumentChangedListener(disposable) {
      createScrollCommand()?.execute()
    }
    component.addComponentResizedListener {
      createScrollCommand()?.let { scrollCommand ->
        if (UIUtil.isFocusAncestor(scrollCommand.component)) {
          scrollCommand.execute()
        }
      }
    }
  }
}

/**
 * This is required to ensure that an editor with disabled scrolling notifies the parent panel
 * that editor size might have changed and the editor needs to be laid out again.
 */
internal suspend fun Editor.revalidateComponentOnTyping(): Nothing {
  useDisposable { disposable ->
    document.addDocumentChangedListener(disposable) {
      component.revalidate()
    }
  }
}

private fun Document.addDocumentChangedListener(
  disposable: Disposable,
  block: () -> Unit,
) {
  addDocumentListener(object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      block()
    }
  }, disposable)
}

private fun JComponent.addComponentResizedListener(
  block: () -> Unit,
) {
  addComponentListener(object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      block()
    }
  })
}

private suspend fun useDisposable(
  block: (Disposable) -> Unit,
): Nothing {
  Disposer.newDisposable().use { disposable ->
    block(disposable)
    awaitCancellation()
  }
}

private class ScrollCommand(
  val component: JComponent,
  val target: Rectangle,
) {
  fun execute() {
    component.scrollRectToVisible(target)
  }
}

private fun Editor.createScrollCommand(): ScrollCommand? {
  if (!component.isShowing) {
    return null
  }
  val caretPoint = visualPositionToXY(caretModel.visualPosition)
  val caretPointInParent = SwingUtilities.convertPoint(contentComponent, caretPoint, component)

  return if (caretPointInParent !in component.visibleRect) {
    val rect = Rectangle(caretPointInParent.x, caretPointInParent.y, 1, lineHeight)
    ScrollCommand(component = component, target = rect)
  }
  else {
    null
  }
}
