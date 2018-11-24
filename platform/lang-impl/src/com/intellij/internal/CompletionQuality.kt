// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.google.common.collect.Lists
import com.google.gson.Gson
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
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
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.NativeFileType
import com.intellij.openapi.fileTypes.impl.FileTypeRenderer
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.ui.ScrollingUtil
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
        val files = if (dialog.scope is GlobalSearchScope) {
          ReadAction.compute<Collection<VirtualFile>, Exception> {
            FileTypeIndex.getFiles(fileType, dialog.scope as GlobalSearchScope)
          }
        }
        else {
          (dialog.scope as LocalSearchScope).virtualFiles.asList()
        }

        val wordSet = HashMap<String, Int>() // we don't want to complete the same words more than twice

        for (file in files) {
          if (indicator.isCanceled) {
            stats.finished = false
            return
          }

          indicator.text = file.path

          val document = ReadAction.compute<Document, Exception> { FileDocumentManager.getInstance().getDocument(file) }

          val completionAttempts = ReadAction.compute<List<Pair<Int, String>>, Exception> {
            getCompletionAttempts(PsiManager.getInstance(project).findFile(file)!!, wordSet)
          }

          if (completionAttempts.isNotEmpty()) {
            val semaphore = Semaphore()
            semaphore.down()

            val application = ApplicationManager.getApplication()
            application.invokeAndWait(Runnable {
              val newEditor = WriteAction.compute<Editor, Exception> {
                val descriptor = OpenFileDescriptor(project, file)
                FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
              }

              val text = document.text

              application.executeOnPooledThread {
                try {
                  for (pair in completionAttempts) {
                    if (indicator.isCanceled) {
                      break
                    }
                    val line = StringUtil.offsetToLineNumber(text, pair.first)
                    evalCompletionAt(project, file.path + ":$line", newEditor, text, pair.first, pair.second, stats, indicator)
                  }
                }
                finally {
                  semaphore.up()
                  application.invokeAndWait(Runnable {
                    WriteAction.run<Exception> {
                      document.setText(text)
                      FileDocumentManager.getInstance().saveDocument(document)
                    }
                  })
                }
              }
            }, ModalityState.NON_MODAL)

            semaphore.waitFor()

            stats.totalFiles += 1
          }
        }
        stats.finished = true

        val gson = Gson()

        UIUtil.invokeLaterIfNeeded { createConsoleAndPrint(project, gson.toJson(stats)) }
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

  private fun getCompletionAttempts(file: PsiFile, wordSet: HashMap<String, Int>): List<Pair<Int, String>> {
    val res = Lists.newArrayList<Pair<Int, String>>()

    val text = file.text

    for (range in StringUtil.getWordIndicesIn(text)) {
      val startIndex = range.startOffset
      if (startIndex != -1) {
        val el = file.findElementAt(startIndex)

        if (el != null && el !is PsiComment) {
          val word = range.substring(text)
          if (!word.isEmpty() && wordSet.getOrDefault(word, 0) < 2) {
            res.add(Pair(startIndex - 1, word))
            wordSet[word] = wordSet.getOrDefault(word, 0) + 1
          }
        }
      }
    }

    return res
  }

  private fun evalCompletionAt(project: Project,
                               path: String,
                               editor: Editor,
                               text: String,
                               startIndex: Int,
                               existingCompletion: String,
                               stats: CompletionStats,
                               indicator: ProgressIndicator) {
    val completionTime = CompletionTime(0, 0)
    val (rank0, total0) = findCorrectElementRank(editor, text, startIndex, 0, project, existingCompletion, completionTime)
    if (indicator.isCanceled) {
      return
    }
    val (rank1, total1) = findCorrectElementRank(editor, text, startIndex, 1, project, existingCompletion, completionTime)
    if (indicator.isCanceled) {
      return
    }
    val (rank3, total3) = findCorrectElementRank(editor, text, startIndex, 3, project, existingCompletion, completionTime)
    if (indicator.isCanceled) {
      return
    }

    val maxChars = 10

    val cache = arrayOfNulls<Pair<Int, Int>>(maxChars)

    val charsToFirst = calcCharsToFirstN(rank0, rank1, rank3, 1, editor, text, startIndex, project, existingCompletion, completionTime,
                                         indicator,
                                         maxChars,
                                         cache)

    val charsToFirst3 = calcCharsToFirstN(rank0, rank1, rank3, 3, editor, text, startIndex, project, existingCompletion, completionTime,
                                          indicator,
                                          maxChars,
                                          cache)


    stats.completions.add(
      Completion(path, startIndex, existingCompletion, rank0, total0, rank1, total1, rank3, total3, charsToFirst, charsToFirst3,
                 completionTime.cnt, completionTime.time))
  }

  private fun calcCharsToFirstN(rank0: Int,
                                rank1: Int,
                                rank3: Int,
                                N: Int,
                                editor: Editor,
                                text: String,
                                startIndex: Int,
                                project: Project,
                                existingCompletion: String,
                                completionTime: CompletionTime,
                                indicator: ProgressIndicator,
                                max: Int,
                                cache: Array<Pair<Int, Int>?>): Int {
    return when {
      rank0 in 0 until N -> 0
      rank1 in 0 until N -> 1
      rank3 in 0 until N -> {
        val (rank2, _) = findCorrectElementRank(editor, text, startIndex, 2, project, existingCompletion, completionTime)
        if (rank2 in 0 until N) {
          2
        }
        else {
          3
        }
      }
      else -> {
        findNumberOfCharsToWin(editor, text, startIndex, project, existingCompletion, indicator, 4, max, N, cache, completionTime)
      }
    }
  }

  private fun findNumberOfCharsToWin(editor: Editor,
                                     text: String,
                                     startIndex: Int,
                                     project: Project,
                                     existingCompletion: String,
                                     indicator: ProgressIndicator,
                                     from: Int,
                                     to: Int,
                                     resultInFirstN: Int,
                                     cache: Array<Pair<Int, Int>?>,
                                     timeStats: CompletionTime): Int {

    for (mid in from until to) {
      if (indicator.isCanceled) {
        return -1
      }

      val (rank, total) = if (cache[mid] != null) {
        cache[mid]!!
      }
      else {
        findCorrectElementRank(editor, text, startIndex, mid, project, existingCompletion, timeStats)
      }
      if (cache[mid] == null) {
        cache[mid] = Pair(rank, total)
      }

      if (rank == -2) {
        return -1
      }

      if (rank < resultInFirstN) {
        return mid
      }
    }

    return -1
  }

  private data class CompletionTime(var cnt: Int, var time: Long)

  private fun findCorrectElementRank(editor: Editor,
                                     text: String,
                                     startIndex: Int,
                                     charsTyped: Int,
                                     project: Project,
                                     existingCompletion: String,
                                     timeStats: CompletionTime): Pair<Int, Int> {
    if (charsTyped > existingCompletion.length) {
      return Pair(-2, 0)
    }
    if (charsTyped == existingCompletion.length) {
      return Pair(0, 1)
    }
    val newText = text.substring(0, startIndex + 1 + charsTyped) + text.substring(startIndex + existingCompletion.length + 1)

    val result = Ref.create(-1)
    val total = Ref.create(0)
    ApplicationManager.getApplication().invokeAndWait(Runnable {
      try {

        val ref: Ref<List<LookupElement>> = Ref.create()

        val start = System.currentTimeMillis()
        CommandProcessor.getInstance().executeCommand(project, {
          WriteAction.run<Exception> {
            editor.document.setText(newText)
            FileDocumentManager.getInstance().saveDocument(editor.document)
            editor.caretModel.moveToOffset(startIndex + 1 + charsTyped)
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
          }

          val handler = object : CodeCompletionHandlerBase(CompletionType.BASIC, false, false, true) {
            override fun completionFinished(indicator: CompletionProgressIndicator, hasModifiers: Boolean) {
              super.completionFinished(indicator, hasModifiers)
              ref.set(indicator.lookup!!.items)
            }

            override fun isExecutedProgrammatically() = true
          }
          handler.invokeCompletion(project, editor, 1)

        }, null, null, editor.document)

        val lookup = LookupManager.getActiveLookup(editor)
        if (lookup != null && lookup is LookupImpl) {
          ScrollingUtil.moveUp(lookup.list, 0)
          lookup.refreshUi(false, false)
          ref.set(lookup.items)
          lookup.hideLookup(true)
        }

        if (!ref.isNull) {
          result.set(ref.get().indexOfFirst { it.lookupString == existingCompletion })
          total.set(ref.get().size)
        }

        timeStats.cnt += 1
        timeStats.time += System.currentTimeMillis() - start
      }
      catch (e: Exception) {
        LOG.error(e)
      }
      finally {
      }
    }, ModalityState.NON_MODAL)


    return Pair(result.get(), total.get())
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
    e.presentation.text = "Completion Quality Statistics"
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
    title = "Completion Quality Statistics"

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

private data class Completion(val path: String,
                              val offset: Int,
                              val word: String,
                              val rank0: Int,
                              val total0: Int,
                              val rank1: Int,
                              val total1: Int,
                              val rank3: Int,
                              val total3: Int,
                              val charsToFirst: Int,
                              val charsToFirst3: Int,
                              val callsCount: Int,
                              val totalTime: Long) {
  val id = (path + ":" + offset.toString()).hashCode()
}

private data class CompletionStats(val timestamp: Long) {
  var finished: Boolean = false
  val completions = Lists.newArrayList<Completion>()
  var totalFiles = 0
}


private val LOG = Logger.getInstance(CompletionQualityStatsAction::class.java)