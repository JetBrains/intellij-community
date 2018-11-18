// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.google.common.collect.Lists
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.*
import com.intellij.openapi.fileTypes.impl.FileTypeRenderer
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
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

    val task = object : Task.Modal(project, "Emulating completion", true) {
      override fun run(indicator: ProgressIndicator) {
        val files = ReadAction.compute<Collection<VirtualFile>, Exception> {
          FileTypeIndex.getFiles(fileType, dialog.scope as GlobalSearchScope)
        }

        for (file in files) {
          if (indicator.isCanceled) {
            stats.finished = false
            return
          }

          indicator.text = file.path

          val document = ReadAction.compute<Document, Exception> { FileDocumentManager.getInstance().getDocument(file) }

          val completionAttempts = ReadAction.compute<List<Pair<Int, String>>, Exception> {
            getCompletionAttempts(PsiManager.getInstance(project).findFile(file)!!)
          }

          if (completionAttempts.isNotEmpty()) {
            val application = ApplicationManager.getApplication()
            application.invokeAndWait(Runnable {
              val newEditor = WriteAction.compute<Editor, Exception> {
                val newPsiFile = PsiFileFactory.getInstance(project).createFileFromText(file.path, (fileType as LanguageFileType).language,
                                                                                        "", true, false)
                val newDocument = PsiDocumentManager.getInstance(project).getDocument(newPsiFile)
                EditorFactory.getInstance().createEditor(newDocument!!, project, fileType, false)
              }

              val modalityState = ModalityState.current()

              val text = document.text

              application.executeOnPooledThread {
                try {
                  for (pair in completionAttempts) {
                    evalCompletionAt(project, file, newEditor, text, pair.first, pair.second, stats, modalityState)
                  }
                }
                finally {
                }
              }
            }, ModalityState.defaultModalityState())
          }
        }
        stats.finished = true
      }
    }

    ProgressManager.getInstance().run(task)
  }

  private fun getCompletionAttempts(file: PsiFile): List<Pair<Int, String>> {
    val res = Lists.newArrayList<Pair<Int, String>>()

    var startIndex = 0
    val text = file.text
    do {
      startIndex = text.indexOf(".", startIndex)
      if (startIndex != -1) {

        val el = file.findElementAt(startIndex)

        if (el != null && el !is PsiComment && el.text == ".") {
          res.add(Pair(startIndex, existingCompletion(startIndex, text)))
        }

        startIndex += 1
      }
    }
    while (startIndex != -1)

    return res
  }

  private fun evalCompletionAt(project: Project,
                               file: VirtualFile,
                               editor: Editor,
                               text: String,
                               startIndex: Int,
                               existingCompletion: String,
                               stats: CompletionStats,
                               modalityState: ModalityState) {
    val newText = text.substring(0, startIndex+1) + text.substring(startIndex + existingCompletion.length + 1)
    ApplicationManager.getApplication().invokeAndWait(Runnable {
      WriteAction.run<Exception> {
        editor.document.setText(newText)
        editor.caretModel.moveToOffset(startIndex + 1)
      }

      val ref: Ref<List<LookupElement>> = Ref.create()

      CommandProcessor.getInstance().executeCommand(project, {
        val handler = object : CodeCompletionHandlerBase(CompletionType.BASIC) {
          override fun completionFinished(indicator: CompletionProgressIndicator, hasModifiers: Boolean) {
            ref.set(indicator.lookup!!.items)
            super.completionFinished(indicator, hasModifiers)
          }
        }
        handler.invokeCompletion(project, editor, 1)
      }, null, null, editor.document)

      if (!ref.isNull) {
        for (item in ref.get()) {
          println(item) //TODO to be continued
        }
      }
      else {
        LOG.info("Lookup is null at ${file.path}:$startIndex")
      }
    }, modalityState)
  }

  private fun existingCompletion(startIndex: Int, text: String): String {
    var i = startIndex + 1
    while (Character.isJavaIdentifierPart(text[i]) && i < text.length) {
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
  var finished: Boolean = false
}


private val LOG = Logger.getInstance(CompletionQualityStatsAction::class.java)