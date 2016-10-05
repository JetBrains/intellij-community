/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.analysis.actions

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiManager

class ToggleInlineHintsAction: AnAction() {
  
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = true

    val isShow = EditorSettingsExternalizable.getInstance().isShowParameterNameHints
    e.presentation.text = if (isShow) "Hide Parameter Name Hints" else "Show Parameter Name Hints"
  }

  override fun actionPerformed(e: AnActionEvent) {
    val settings = EditorSettingsExternalizable.getInstance()
    val before = settings.isShowParameterNameHints
    settings.isShowParameterNameHints = !before
    
    ProjectManager.getInstance().openProjects.forEach {
      val psiManager = PsiManager.getInstance(it)
      val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(it)
      val fileEditorManager = FileEditorManager.getInstance(it)
      
      fileEditorManager.selectedFiles.forEach {
        psiManager.findFile(it)?.let { daemonCodeAnalyzer.restart(it) }
      }
    }
  }
  
}