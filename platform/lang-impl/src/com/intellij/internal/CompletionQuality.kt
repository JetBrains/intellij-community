// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.google.common.collect.Lists
import com.google.gson.Gson
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
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
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
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
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
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

    val stats = CompletionStats(System.currentTimeMillis())

    val task = object : Task.Backgroundable(project, "Emulating completion", true) {
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
            val semaphore = Semaphore()
            semaphore.down()

            val application = ApplicationManager.getApplication()
            application.invokeAndWait(Runnable {
              val newEditor = WriteAction.compute<Editor, Exception> {
                val newPsiFile = PsiFileFactory.getInstance(project).createFileFromText(file.path, (fileType as LanguageFileType).language,
                                                                                        "", true, false)
                val newDocument = PsiDocumentManager.getInstance(project).getDocument(newPsiFile)
                EditorFactory.getInstance().createEditor(newDocument!!, project, fileType, false)
              }

              val text = document.text

              application.executeOnPooledThread {
                try {
                  for (pair in completionAttempts) {
                    if (indicator.isCanceled) {
                      break
                    }
                    evalCompletionAt(project, file, newEditor, text, pair.first, pair.second, stats, indicator)
                  }
                }
                finally {
                  semaphore.up()
                }
              }
            }, ModalityState.NON_MODAL)

            semaphore.waitFor()
          }
        }
        stats.finished = true

        var gson = Gson()

        UIUtil.invokeLaterIfNeeded {  createConsoleAndPrint(project, gson.toJson(stats)) }
      }
    }

    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
  }

  private fun createConsoleAndPrint(project: Project, text: String) {
    val consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
    val console = consoleBuilder.console
    val descriptor = RunContentDescriptor(console, null, console.component, "Completion Quality Statistics")
    ExecutionManager.getInstance(project).contentManager.showRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor)
    console.print(text, ConsoleViewContentType.NORMAL_OUTPUT)
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
                               indicator: ProgressIndicator) {
    val rank0 = findCorrectElementRank(editor, text, startIndex, 0, project, existingCompletion, file)
    if (indicator.isCanceled) {
      return
    }
    val rank1 = findCorrectElementRank(editor, text, startIndex, 1, project, existingCompletion, file)
    if (indicator.isCanceled) {
      return
    }
    val rank3 = findCorrectElementRank(editor, text, startIndex, 3, project, existingCompletion, file)
    if (indicator.isCanceled) {
      return
    }

    val charsToWin = when {
      rank0 == 0 -> 0
      rank1 == 0 -> 1
      rank3 == 0 -> {
        val rank2 = findCorrectElementRank(editor, text, startIndex, 2, project, existingCompletion, file)
        if (rank2 == 0) {
          2
        }
        else {
          3
        }
      }
      else -> {
        findNumberOfCharsToWin(editor, text, startIndex, project, existingCompletion, file, indicator, 4)
      }
    }

    stats.completions.add(Completion(file.path, startIndex, rank0, rank1, rank3, charsToWin))
  }

  private fun findNumberOfCharsToWin(editor: Editor,
                                     text: String,
                                     startIndex: Int,
                                     project: Project,
                                     existingCompletion: String,
                                     file: VirtualFile,
                                     indicator: ProgressIndicator,
                                     offset: Int): Int {
    for (i in offset..10) {
      if (indicator.isCanceled) {
        return -1
      }
      val ranki = findCorrectElementRank(editor, text, startIndex, i, project, existingCompletion, file)
      if (ranki == 1) {
        return i
      }
    }
    return -1
  }

  private fun findCorrectElementRank(editor: Editor,
                                     text: String,
                                     startIndex: Int,
                                     charsTyped: Int,
                                     project: Project,
                                     existingCompletion: String,
                                     file: VirtualFile): Int {
    if (charsTyped > existingCompletion.length) {
      return -2
    }
    val newText = text.substring(0, startIndex + 1 + charsTyped) + text.substring(startIndex + existingCompletion.length + 1)

    val result = Ref.create(-1)
    ApplicationManager.getApplication().invokeAndWait(Runnable {
      WriteAction.run<Exception> {
        editor.document.setText(newText)
        editor.caretModel.moveToOffset(startIndex + 1 + charsTyped)
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
        result.set(ref.get().indexOfFirst { it.lookupString == existingCompletion })
      }
      else {
        LOG.info("Lookup is null at ${file.path}:$startIndex")
      }
    }, ModalityState.NON_MODAL)

    return result.get()
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

private data class Completion(val path: String, val offset: Int, val rank0: Int, val rank1: Int, val rank3: Int, val charsToWin: Int)

private data class CompletionStats(val timestamp: Long) {
  var finished: Boolean = false
  val completions = Lists.newArrayList<Completion>()
}


private val LOG = Logger.getInstance(CompletionQualityStatsAction::class.java)