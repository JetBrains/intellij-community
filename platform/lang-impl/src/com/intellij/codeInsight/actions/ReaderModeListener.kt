// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions

import com.intellij.application.options.colors.ReaderModeStatsCollector
import com.intellij.codeInsight.actions.ReaderModeSettings.Companion.applyReaderMode
import com.intellij.codeInsight.actions.ReaderModeSettingsListener.Companion.applyToAllEditors
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
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
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.messages.Topic
import com.intellij.util.ui.UIUtil
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.event.HyperlinkListener

interface ReaderModeListener : EventListener {
  fun modeChanged(project: Project)
}

class ReaderModeSettingsListener : ReaderModeListener {
  companion object {
    @Topic.ProjectLevel
    @JvmStatic
    val TOPIC = Topic(ReaderModeListener::class.java, Topic.BroadcastDirection.NONE)

    fun applyToAllEditors(project: Project, preferGlobalSettings: Boolean = false) {
      FileEditorManager.getInstance(project).allEditors.forEach {
        if (it !is TextEditor) return
        applyReaderMode(project, it.editor, it.file, fileIsOpenAlready = true, preferGlobalSettings = preferGlobalSettings)
      }

      EditorFactory.getInstance().allEditors.forEach {
        if (it !is EditorImpl) return
        applyReaderMode(project, it, FileDocumentManager.getInstance().getFile(it.document),
                        fileIsOpenAlready = true, preferGlobalSettings = preferGlobalSettings)
      }
    }

    fun createReaderModeComment() = HyperlinkLabel().apply {
      setTextWithHyperlink(IdeBundle.message("checkbox.also.in.reader.mode"))
      font = UIUtil.getFont(UIUtil.FontSize.SMALL, font)
      foreground = UIUtil.getLabelFontColor(UIUtil.FontColor.BRIGHTER)
      addHyperlinkListener(HyperlinkListener {
        DataManager.getInstance().dataContextFromFocusAsync.onSuccess { context ->
          context?.let { dataContext ->
            Settings.KEY.getData(dataContext)?.let { settings ->
              settings.select(settings.find("editor.reader.mode"))
              ReaderModeStatsCollector.logSeeAlsoNavigation()
            }
          }
        }
      })
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
          applyToAllEditors(project, true)
        }
      }
    }
    EditorSettingsExternalizable.getInstance().addPropertyChangeListener(propertyChangeListener, project)

    val fontPreferences = AppEditorFontOptions.getInstance().fontPreferences as FontPreferencesImpl
    fontPreferences.changeListener = Runnable {
      fontPreferences.changeListener
      ReaderModeSettings.instance(project).showLigatures = fontPreferences.useLigatures()
      applyToAllEditors(project, true)
    }

    Disposer.register(project) { fontPreferences.changeListener = null }
  }
}