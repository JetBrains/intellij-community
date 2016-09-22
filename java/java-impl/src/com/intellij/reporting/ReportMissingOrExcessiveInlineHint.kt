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
package com.intellij.reporting

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import java.io.File

class ReportMissingOrExcessiveInlineHint : AnAction() {
  
  private val text = "Report Missing or Excessive Inline Hint"
  private val description = "Text line at caret will be anonymously reported to our servers"
  
  companion object {
    private val LOG = Logger.getInstance(ReportMissingOrExcessiveInlineHint::class.java)
  }
  
  init {
    val presentation = templatePresentation
    presentation.text = text
    presentation.description = description
  }
  
  private val recorderId = "inline-hints-reports"
  private val file = File(PathManager.getTempPath(), recorderId)
  
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    
    if (!isHintsEnabled()) return
    CommonDataKeys.PROJECT.getData(e.dataContext) ?: return
    val editor = CommonDataKeys.EDITOR.getData(e.dataContext) ?: return
    
    val range = getCurrentLineRange(editor)
    if (editor.getInlays(range).isNotEmpty()) {
      e.presentation.isEnabledAndVisible = true
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = CommonDataKeys.PROJECT.getData(e.dataContext)!!
    val editor = CommonDataKeys.EDITOR.getData(e.dataContext)!!
    val document = editor.document

    val range = getCurrentLineRange(editor)
    val inlays = editor.getInlays(range)

    if (inlays.isNotEmpty()) {
      val line = document.getText(range)
      reportInlays(line.trim(), inlays)
      showHint(project)
    }
  }
  
  private fun reportInlays(text: String, inlays: List<Inlay>) {
    val hintManager = ParameterHintsPresentationManager.getInstance()
    val hints = inlays.mapNotNull { hintManager.getHintText(it) }
    trySend(text, hints)
  }

  private fun trySend(text: String, inlays: List<String>) {
    val report = InlayReport(text, inlays)
    writeToFile(createReportLine(recorderId, report))
    trySendFileInBackground()
  }

  private fun trySendFileInBackground() {
    LOG.debug("File: ${file.path} Length: ${file.length()}")
    if (!file.exists() || file.length() == 0L) return
    ApplicationManager.getApplication().executeOnPooledThread {
      val text = file.readText()
      LOG.debug("File text $text")
      if (StatsSender.send(text, compress = false)) {
        file.delete()
        LOG.debug("File deleted")
      }
    }
  }
  
  private fun showHint(project: Project) {
    val notification = Notification(
        "Inline Hints",
        "Inline Hints Reporting",
        "Problematic inline hint was reported",
        NotificationType.INFORMATION
    )
    notification.notify(project)
  }

  private fun writeToFile(line: String) {
    if (!file.exists()) {
      file.createNewFile()
    }
    file.appendText(line)
  }

  private fun getCurrentLineRange(editor: Editor): TextRange {
    val offset = editor.caretModel.currentCaret.offset
    val document = editor.document
    val line = document.getLineNumber(offset)
    return TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line))
  }

}

private fun isHintsEnabled() = EditorSettingsExternalizable.getInstance().isShowParameterNameHints

private fun Editor.getInlays(range: TextRange): List<Inlay> {
  return inlayModel.getInlineElementsInRange(range.startOffset, range.endOffset)
}

private class InlayReport(@JvmField var text: String, @JvmField var inlays: List<String>)