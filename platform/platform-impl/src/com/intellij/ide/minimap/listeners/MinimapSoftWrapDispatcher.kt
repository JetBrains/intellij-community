// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.listeners

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.ex.SoftWrapModelEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import java.util.concurrent.CopyOnWriteArrayList

internal class MinimapSoftWrapDispatcher private constructor(
  private val editor: Editor,
) : SoftWrapChangeListener, Disposable {
  private val listeners = CopyOnWriteArrayList<SoftWrapChangeListener>()

  fun subscribe(listener: SoftWrapChangeListener, parentDisposable: Disposable) {
    listeners.add(listener)
    Disposer.register(parentDisposable) {
      listeners.remove(listener)
    }
  }

  override fun softWrapsChanged() {
    for (listener in listeners) {
      listener.softWrapsChanged()
    }
  }

  override fun recalculationEnds() {
    for (listener in listeners) {
      listener.recalculationEnds()
    }
  }

  override fun dispose() {
    editor.putUserData(KEY, null)
  }

  companion object {
    private val KEY: Key<MinimapSoftWrapDispatcher> = Key.create("com.intellij.ide.minimap.softWrapDispatcher")

    fun getOrCreate(editor: Editor): MinimapSoftWrapDispatcher? {
      editor.getUserData(KEY)?.let { return it }

      val softWrapModel = editor.softWrapModel as? SoftWrapModelEx ?: return null
      val dispatcher = MinimapSoftWrapDispatcher(editor)
      editor.putUserData(KEY, dispatcher)
      softWrapModel.addSoftWrapChangeListener(dispatcher)
      EditorUtil.disposeWithEditor(editor, dispatcher)
      return dispatcher
    }
  }
}
