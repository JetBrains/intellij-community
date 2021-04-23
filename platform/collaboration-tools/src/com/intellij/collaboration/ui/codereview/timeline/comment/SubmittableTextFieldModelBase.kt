// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.timeline.comment

import com.intellij.collaboration.ui.codereview.SimpleEventListener
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher

abstract class SubmittableTextFieldModelBase(initialText: String) : SubmittableTextFieldModel {
  override val project: Project? = null

  private val listeners = EventDispatcher.create(SimpleEventListener::class.java)

  override val document = EditorFactory.getInstance().createDocument(initialText)

  override var isBusy = false
    set(value) {
      field = value
      listeners.multicaster.eventOccurred()
    }

  override var error: Throwable? = null
    set(value) {
      field = value
      listeners.multicaster.eventOccurred()
    }

  override fun addStateListener(listener: SimpleEventListener) {
    listeners.addListener(listener)
  }
}