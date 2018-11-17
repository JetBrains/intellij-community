// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.NativeFileType
import com.intellij.openapi.fileTypes.impl.FileTypeRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.ui.layout.*
import java.util.*
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * @author traff
 */
class CompletionQualityStatsAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) as? EditorImpl
    val project = e.getData(CommonDataKeys.PROJECT) ?: return

    val dialog = CompletionQualityDialog(project, editor)
    if (!dialog.showAndGet()) return
    val fileType = dialog.fileType

    val stats = CompletionStats()

    for (it in FileTypeIndex.getFiles(fileType, dialog.scope as GlobalSearchScope)) {
      calcCompletionQuality(project, it, stats)
    }
  }

  private fun calcCompletionQuality(project: Project,
                                    file: VirtualFile,
                                    stats: CompletionStats) {
    val document = FileDocumentManager.getInstance().getDocument(file)
    var startIndex = 0
    if (document == null) {
      return
    }
    do {
      startIndex = document.text.indexOf(".", startIndex)
      if (startIndex != -1) {
        evalCompletionAt(project, file, document, startIndex, existingCompletion(startIndex, document.text), stats)
        startIndex +=1
      }
    } while (startIndex != -1)
  }

  private fun evalCompletionAt(project: Project,
                               file: VirtualFile,
                               document: Document,
                               startIndex: Int,
                               existingCompletion: String,
                               stats: CompletionStats) {
    val newText = document.text.substring(0, startIndex) + document.text.substring(startIndex + existingCompletion.length)
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
    if (psiFile != null) {
      val newDocument = EditorFactory.getInstance().createDocument(newText)
      val editor = EditorFactory.getInstance().createEditor(newDocument, project)
      editor.caretModel.moveToOffset(startIndex+1)
      CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, editor)
      val lookup = LookupManager.getActiveLookup(editor)
      if (lookup != null) {
        for (item in lookup.items) {
          println(item)
        }
      } else {
        LOG.info("Lookup is null at ${file.path}:$startIndex")
      }
    } else {
      LOG.info("PSIFile is null for " + file.path)
    }
  }

  private fun existingCompletion(startIndex: Int, text: String): String {
    var i = startIndex + 1
    while (Character.isJavaIdentifierPart(text[i]) && i<text.length) {
      i++
    }
    return text.substring(startIndex + 1, i)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
    e.presentation.text = "Completion Quality Stats"
  }
}


class CompletionQualityDialog(project: Project, private val editor: Editor?) : DialogWrapper(project) {
  private var fileTypeCombo: JComboBox<FileType>

  private var scopeChooserCombo: ScopeChooserCombo

  var fileType: FileType
    get() = fileTypeCombo.selectedItem as FileType
    private set(_) {}

  var scope: SearchScope?
    get() = scopeChooserCombo.selectedScope
    private set(_) {}


  init {
    title = "Completion Quality Stats"

    fileTypeCombo = createFileTypesCombo()

    scopeChooserCombo = ScopeChooserCombo(project, false, true, "")

    if (editor != null) {
      PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.let {
        fileTypeCombo.selectedItem = it.fileType
      }
    }

    init()
  }

  private fun createFileTypesCombo(): ComboBox<FileType> {
    val fileTypes = FileTypeManager.getInstance().registeredFileTypes
    Arrays.sort(fileTypes) { ft1, ft2 ->
      if (ft1 == null) 1
      else if (ft2 == null) -1
      else ft1.description.compareTo(ft2.description, ignoreCase = true)
    }

    val model = DefaultComboBoxModel<FileType>()
    for (type in fileTypes) {
      if (!type.isReadOnly && type !== FileTypes.UNKNOWN && type !is NativeFileType) {
        model.addElement(type)
      }
    }

    val combo = ComboBox<FileType>(model)

    combo.renderer = FileTypeRenderer()

    return combo
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(label = JLabel("File type:")) {
        fileTypeCombo()
      }
      row(label = JLabel("Scope:")) {
        scopeChooserCombo()
      }
    }
  }
}

class CompletionStats {

}


private val LOG = Logger.getInstance(CompletionQualityStatsAction::class.java)