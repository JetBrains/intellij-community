// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions

import com.intellij.codeInsight.actions.ReaderModeSettings.Companion.applyReaderMode
import com.intellij.codeInsight.actions.ReaderModeSettingsListener.Companion.applyToAllEditors
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
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

    fun applyToAllEditors(project: Project, preferGlobalSettings: Boolean = false) {
      FileEditorManager.getInstance(project).allEditors.forEach {
        if (it !is PsiAwareTextEditorImpl) return
        applyReaderMode(project, it.editor, it.file, fileIsOpenAlready = true, preferGlobalSettings = preferGlobalSettings)
      }

      EditorFactory.getInstance().allEditors.forEach {
        if (it !is EditorImpl) return
        applyReaderMode(project, it, FileDocumentManager.getInstance().getFile(it.document),
                        fileIsOpenAlready = true, preferGlobalSettings = preferGlobalSettings)
      }
    }
  }

  override fun modeChanged(project: Project) = applyToAllEditors(project)
}

class ReaderModeEditorSettingsListener : StartupActivity, DumbAware {
  override fun runActivity(project: Project) {
    val propertyChangeListener = PropertyChangeListener { event ->
      when (event.propertyName) {
        EditorSettingsExternalizable.PROP_BREADCRUMBS_PER_LANGUAGE -> applyToAllEditors(project, true)
        EditorSettingsExternalizable.PROP_DOC_COMMENT_RENDERING -> applyToAllEditors(project, true)
      }
    }
    EditorSettingsExternalizable.getInstance().addPropertyChangeListener(propertyChangeListener)
    Disposer.register(project) { EditorSettingsExternalizable.getInstance().removePropertyChangeListener(propertyChangeListener) }
  }
}