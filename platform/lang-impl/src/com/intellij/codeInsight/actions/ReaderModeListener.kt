// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.application.options.colors.ReaderModeStatsCollector
import com.intellij.codeInsight.actions.ReaderModeSettingsListener.Companion.applyToAllEditors
import com.intellij.codeWithMe.ClientId
import com.intellij.ide.DataManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.ClientEditorManager
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.ClientFileEditorManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import java.beans.PropertyChangeListener
import java.util.*

interface ReaderModeListener : EventListener {
  fun modeChanged(project: Project)
}

class ReaderModeSettingsListener : ReaderModeListener {
  companion object {
    @Topic.ProjectLevel
    @JvmField
    val TOPIC: Topic<ReaderModeListener> = Topic(ReaderModeListener::class.java, Topic.BroadcastDirection.NONE)

    @RequiresEdt
    fun applyToAllEditors(project: Project) {
      for (editor in FileEditorManager.getInstance(project).allEditors) {
        if ((ClientFileEditorManager.getClientId(editor) ?: ClientId.localId) != ClientId.current) continue
        if (editor is TextEditor) {
          ReaderModeSettings.applyReaderMode(project, editor.editor, editor.file, fileIsOpenAlready = true)
        }
      }

      for (editor in ClientEditorManager.getCurrentInstance().editors()) {
        if (editor !is EditorImpl || editor.getProject() != project) {
          continue
        }

        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: continue
        ReaderModeSettings.applyReaderMode(project = project, editor = editor, file = file, fileIsOpenAlready = true)
      }
    }

    fun goToEditorReaderMode() {
      DataManager.getInstance().dataContextFromFocusAsync.onSuccess { context ->
        context?.let { dataContext ->
          Settings.KEY.getData(dataContext)?.let { settings ->
            settings.select(settings.find("editor.reader.mode"))
            ReaderModeStatsCollector.logSeeAlsoNavigation()
          }
        }
      }
    }
  }

  override fun modeChanged(project: Project) {
    if (!project.isDefault) {
      applyToAllEditors(project)
    }
  }
}

private class ReaderModeEditorSettingsListener : ProjectActivity {
  override suspend fun execute(project: Project) {
    val propertyChangeListener = PropertyChangeListener { event ->
      when (event.propertyName) {
        EditorSettingsExternalizable.PROP_DOC_COMMENT_RENDERING -> {
          ReaderModeSettings.getInstance(project).showRenderedDocs = EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled
          applyToAllEditors(project)
        }
      }
    }
    serviceAsync<EditorSettingsExternalizable>().addPropertyChangeListener(propertyChangeListener, project)

    val fontPreferences = serviceAsync<AppEditorFontOptions>().fontPreferences as FontPreferencesImpl
    fontPreferences.addChangeListener({
      ReaderModeSettings.getInstance(project).showLigatures = fontPreferences.useLigatures()
      applyToAllEditors(project)
    }, project)
  }
}