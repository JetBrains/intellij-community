// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.application.options.colors.ReaderModeStatsCollector
import com.intellij.codeInsight.actions.ReaderModeSettingsListener.Companion.applyToAllEditors
import com.intellij.ide.DataManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.ClientEditorManager
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import java.beans.PropertyChangeListener
import java.util.EventListener
import org.jetbrains.annotations.ApiStatus

interface ReaderModeListener : EventListener {
  fun modeChanged(project: Project)
}

@ApiStatus.Internal
class ReaderModeSettingsListener : ReaderModeListener {
  @ApiStatus.Internal
  companion object {
    @Topic.ProjectLevel
    @JvmField
    val TOPIC: Topic<ReaderModeListener> = Topic(ReaderModeListener::class.java, Topic.BroadcastDirection.NONE)

    @RequiresEdt
    fun applyToAllEditors(project: Project) {
      for (editor in FileEditorManager.getInstance(project).allEditors) {
        if (editor is TextEditor) {
          ReaderModeSettings.applyReaderMode(project, editor.editor, editor.file, fileIsOpenAlready = true)
        }
      }

      for (editor in ClientEditorManager.getCurrentInstance().editorsSequence()) {
        if (editor !is EditorImpl || editor.project != project) {
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

internal class ReaderModeEditorSettingsListener : ProjectActivity {
  override suspend fun execute(project: Project) {
    val propertyChangeListener = PropertyChangeListener { event ->
      when (event.propertyName) {
        EditorSettingsExternalizable.PropNames.PROP_ENABLE_RENDERED_DOC -> {
          ReaderModeSettings.getInstance(project).showRenderedDocs = EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled
          applyToAllEditors(project)
        }
      }
    }
    // TODO change to serviceAsync<...> when it is fixed for per-client services
    service<EditorSettingsExternalizable>().addPropertyChangeListener(propertyChangeListener, project)

    val fontPreferences = serviceAsync<AppEditorFontOptions>().fontPreferences as FontPreferencesImpl
    fontPreferences.addChangeListener({
      ReaderModeSettings.getInstance(project).showLigatures = fontPreferences.useLigatures()
      applyToAllEditors(project)
    }, project)
  }
}