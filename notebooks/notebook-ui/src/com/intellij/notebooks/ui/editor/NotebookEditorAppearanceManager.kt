// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.editor

import com.intellij.notebooks.ui.NotebookPluginDisposable
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.EventDispatcher
import com.intellij.util.messages.MessageBusConnection

@Service(Service.Level.APP)
internal class NotebookEditorAppearanceManager {

  private val connection: MessageBusConnection = ApplicationManager.getApplication().getMessageBus().connect(NotebookPluginDisposable.getInstance() as Disposable)

  private val eventDispatcher = EventDispatcher.create(EditorColorsListener::class.java)

  init {
    connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { scheme ->
      eventDispatcher.multicaster.globalSchemeChange(scheme)
    })
  }

  fun addEditorColorsListener(parentDisposable: Disposable, listener: EditorColorsListener) {
    eventDispatcher.addListener(listener, parentDisposable)
  }
}