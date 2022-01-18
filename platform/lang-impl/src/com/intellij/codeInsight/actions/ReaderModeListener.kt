// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions

import com.intellij.application.options.colors.ReaderModeStatsCollector
import com.intellij.codeInsight.actions.ReaderModeSettings.Companion.applyReaderMode
import com.intellij.codeInsight.actions.ReaderModeSettingsListener.Companion.applyToAllEditors
import com.intellij.ide.DataManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.messages.Topic
import java.beans.PropertyChangeListener
import java.util.*

interface ReaderModeListener : EventListener {
  fun modeChanged(project: Project)
}

class ReaderModeSettingsListener : ReaderModeListener {
  companion object {
    @Topic.ProjectLevel
    @JvmStatic
    val TOPIC = Topic(ReaderModeListener::class.java, Topic.BroadcastDirection.NONE)

    fun applyToAllEditors(project: Project) {
      FileEditorManager.getInstance(project).allEditors.forEach {
        if (it !is TextEditor) return@forEach
        applyReaderMode(project, it.editor, it.file, fileIsOpenAlready = true)
      }

      EditorFactory.getInstance().allEditors.forEach {
        if (it !is EditorImpl) return@forEach
        if (it.getProject() != project) return@forEach
        applyReaderMode(project, it, FileDocumentManager.getInstance().getFile(it.document),
                        fileIsOpenAlready = true)
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

  override fun modeChanged(project: Project) = applyToAllEditors(project)
}

internal class ReaderModeEditorSettingsListener : StartupActivity, DumbAware {
  override fun runActivity(project: Project) {
    val propertyChangeListener = PropertyChangeListener { event ->
      when (event.propertyName) {
        EditorSettingsExternalizable.PROP_DOC_COMMENT_RENDERING -> {
          ReaderModeSettings.instance(project).showRenderedDocs = EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled
          applyToAllEditors(project)
        }
      }
    }
    EditorSettingsExternalizable.getInstance().addPropertyChangeListener(propertyChangeListener, project)

    val fontPreferences = AppEditorFontOptions.getInstance().fontPreferences as FontPreferencesImpl
    fontPreferences.changeListener = Runnable {
      fontPreferences.changeListener
      ReaderModeSettings.instance(project).showLigatures = fontPreferences.useLigatures()
      applyToAllEditors(project)
    }

    Disposer.register(project) { fontPreferences.changeListener = null }
  }
}