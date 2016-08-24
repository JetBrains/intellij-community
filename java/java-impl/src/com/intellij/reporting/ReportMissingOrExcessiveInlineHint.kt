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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomTextElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiMethod
import java.io.File

class ReportMissingOrExcessiveInlineHint : AnAction() {
  private val recorderId = "inline-hints-reports"
  private val file = File(PathManager.getTempPath(), recorderId)
  
  override fun update(e: AnActionEvent) {
    if (!isHintsEnabled()) return
    CommonDataKeys.EDITOR.getData(e.dataContext) ?: return
    if (CommonDataKeys.PSI_ELEMENT.getData(e.dataContext) is PsiMethod) {
      e.presentation.isEnabled = true
    }
  }

  private fun isHintsEnabled() = Registry.`is`("editor.inline.parameter.hints")

  override fun actionPerformed(e: AnActionEvent) {
    val editor = CommonDataKeys.EDITOR.getData(e.dataContext)!!
    val document = editor.document

    val range = getCurrentLineRange(document, editor)
    val inlays = editor.inlayModel.getInlineElementsInRange(range.startOffset, range.endOffset)

    if (inlays.isNotEmpty()) {
      val line = document.getText(range)
      reportInlays(line.trim(), inlays)
    }
  }
  
  private fun reportInlays(text: String, inlays: List<Inlay>) {
    val hints = inlays
        .map { it.renderer }
        .mapNotNull { if (it is EditorCustomTextElementRenderer) it else null }
        .map { it.text }
    
    dump(text, hints)
  }

  private fun dump(text: String, inlays: List<String>) {
    val report = InlayReport(text, inlays)
    writeToFile(createReportLine(recorderId, report))
    trySendFileInBackground()
  }

  private fun trySendFileInBackground() {
    if (!file.exists() || file.length() == 0L) return

    ApplicationManager.getApplication().executeOnPooledThread {
      val text = file.readText()
      if (StatsSender.send(text)) {
        file.delete()
      }
    }
  }

  private fun writeToFile(line: String) {
    if (!file.exists()) {
      file.createNewFile()
    }
    file.appendText(line)
  }

  private fun getCurrentLineRange(document: Document, editor: Editor): TextRange {
    val offset = editor.caretModel.currentCaret.offset
    val line = document.getLineNumber(offset)
    return TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line))
  }

}


class InlayReport(var text: String, var inlays: List<String>)