// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("DEPRECATION")

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
import com.intellij.openapi.application.*
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
import com.intellij.util.ui.UIUtil
import java.util.*
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * @author traff
 */

private data class CompletionTime(var cnt: Int, var time: Long)

private data class CompletionParameters(
  val project: Project,
  val path: String,
  val editor: Editor,
  val text: String,
  val startIndex: Int,
  val word: String,
  val stats: CompletionStats,
  val indicator: ProgressIndicator,
  val completionTime : CompletionTime = CompletionTime(0, 0))

private const val RANK_EXCESS_LETTERS: Int = -2
private const val RANK_NOT_FOUND: Int = -1

private val interestingRanks : IntArray = intArrayOf(0, 1, 3)

class CompletionQualityStatsAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val application = ApplicationManager.getApplication()
    val editor = e.getData(CommonDataKeys.EDITOR) as? EditorImpl
    val project = e.getData(CommonDataKeys.PROJECT) ?: return

    val dialog = CompletionQualityDialog(project, editor)
    if (!dialog.showAndGet()) return
    val fileType = dialog.fileType

    val stats = CompletionStats(System.currentTimeMillis())

    val task = object : Task.Backgroundable(project, "Emulating completion", true) {
      override fun run(indicator: ProgressIndicator) {
        val files = (if (dialog.scope is GlobalSearchScope) {
          application.runReadAction<Collection<VirtualFile>, Exception> {
            FileTypeIndex.getFiles(fileType, dialog.scope as GlobalSearchScope)
          }
        }
        else {
          (dialog.scope as LocalSearchScope).virtualFiles.asList()
        }).sortedBy { it.name } // sort files to have same order each run

        // map to count words frequency
        // we don't want to complete the same words more than twice
        val wordsFrequencyMap = HashMap<String, Int>()

        var filesProcessed = 0 // for show progress fraction info
        for (file in files) {
          if (indicator.isCanceled) {
            stats.finished = false
            return
          }

          filesProcessed += 1
          val procentage = filesProcessed.toDouble() / files.size.toDouble()
          indicator.fraction = procentage

          indicator.text = file.path

          val document = application.runReadAction<Document, Exception> { FileDocumentManager.getInstance().getDocument(file) }

          val completionAttempts = application.runReadAction<List<Pair<Int, String>>, Exception> {
            getCompletionAttempts(PsiManager.getInstance(project).findFile(file)!!, wordsFrequencyMap)
          }

          if (completionAttempts.isNotEmpty()) {
            lateinit var newEditor: Editor
            application.invokeAndWait(Runnable {
              newEditor = application.runWriteAction<Editor, Exception> {
                val descriptor = OpenFileDescriptor(project, file)
                FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
              }
            }, ModalityState.NON_MODAL)

            val text = document.text
            try {
              for ((offset, word) in completionAttempts) {
                if (indicator.isCanceled) {
                  break
                }
                val line = StringUtil.offsetToLineNumber(text, offset)
                evalCompletionAt(CompletionParameters(project, file.path + ":$line", newEditor, text, offset, word, stats, indicator))
              }
            }
            finally {
              application.invokeAndWait(Runnable {
                application.runWriteAction {
                  document.setText(text)
                  FileDocumentManager.getInstance().saveDocument(document)
                }
              })
            }

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

  // Find offsets to words and words on which we want to try completion
  private fun getCompletionAttempts(file: PsiFile, wordSet: HashMap<String, Int>): List<Pair<Int, String>> {
    val max_word_frequency = 2
    val res = Lists.newArrayList<Pair<Int, String>>()
    val text = file.text

    for (range in StringUtil.getWordIndicesIn(text)) {
      val startIndex = range.startOffset
      if (startIndex != -1) {
        val el = file.findElementAt(startIndex)

        if (el != null && el !is PsiComment) {
          val word = range.substring(text)
          if (!word.isEmpty() && wordSet.getOrDefault(word, 0) < max_word_frequency) {
            res.add(Pair(startIndex - 1, word))
            wordSet[word] = wordSet.getOrDefault(word, 0) + 1
          }
        }
      }
    }

    return res
  }

  private fun evalCompletionAt(params: CompletionParameters) {
    with(params) {
      // (typed letters, rank, total)
      val ranks : ArrayList<Triple<Int, Int, Int>> = arrayListOf()
      for (charsTyped in interestingRanks) {
        val (rank, total) = findCorrectElementRank(charsTyped, params)
        ranks.add(Triple(charsTyped, rank, total))
        if (indicator.isCanceled) {
          return
        }
      }

      val maxChars = 10

      val cache = arrayOfNulls<Pair<Int, Int>>(maxChars)

      val charsToFirst = calcCharsToFirstN(ranks, 1, maxChars, cache, params)

      val charsToFirst3 = calcCharsToFirstN(ranks, 3, maxChars, cache, params)

      stats.completions.add(
        Completion(path, startIndex, word,
                   ranks[0].second, ranks[0].third, ranks[1].second, ranks[1].third, ranks[2].second, ranks[2].third,
                   charsToFirst, charsToFirst3, completionTime.cnt, completionTime.time))
    }
  }

  // Calculate number of letters needed to type to have necessary word in top N
  private fun calcCharsToFirstN(ranks: ArrayList<Triple<Int, Int, Int>>,
                                N: Int,
                                max: Int,
                                cache: Array<Pair<Int, Int>?>,
                                params: CompletionParameters): Int {
    var lastCharsTyped = -1
    for ((charsTyped, rank, _) in ranks) {
      assert(lastCharsTyped < charsTyped)
      for (chars in lastCharsTyped + 1 until charsTyped) {
        val (tryRank, _) = findCorrectElementRank(chars, params)
        if (tryRank in 0 until N) {
          return chars
        }
      }
      if (rank in 0 until N) {
        return rank
      }
      lastCharsTyped = charsTyped
    }
    return calcCharsToFirstN(ranks.last().first, max, N, cache, params)
  }

  // Iterate and check from 'from' to 'to'
  private fun calcCharsToFirstN(from: Int,
                                to: Int,
                                N: Int,
                                cache: Array<Pair<Int, Int>?>,
                                params: CompletionParameters): Int {
    with (params) {
      for (mid in from until to) {
        if (indicator.isCanceled) {
          return -1
        }

        val (rank, total) = cache[mid] ?: findCorrectElementRank(mid, params)

        if (cache[mid] == null) {
          cache[mid] = kotlin.Pair(rank, total)
        }

        if (rank == RANK_EXCESS_LETTERS) {
          return -1
        }

        if (rank < N) {
          return mid
        }
      }
      return -1
    }

  }

  // Find position necessary word in lookup list after 'charsTyped' typed letters
  private fun findCorrectElementRank(charsTyped: Int, params: CompletionParameters): Pair<Int, Int> {
    with (params) {
      if (charsTyped > word.length) {
        return Pair(RANK_EXCESS_LETTERS, 0)
      }
      if (charsTyped == word.length) {
        return Pair(0, 1)
      }

      // text with prefix of word of charsTyped length in completion site
      val newText = text.substring(0, startIndex + 1 + charsTyped) + text.substring(startIndex + word.length + 1)

      var result = RANK_NOT_FOUND
      var total = 0
      ApplicationManager.getApplication().invokeAndWait(Runnable {
        try {
          fun getLookupItems() : List<LookupElement>? {
            var lookupItems: List<LookupElement>? = null

            CommandProcessor.getInstance().executeCommand(project, {
              WriteAction.run<Exception> {
                editor.document.setText(newText)
                FileDocumentManager.getInstance().saveDocument(editor.document)
                editor.caretModel.moveToOffset(startIndex + 1 + charsTyped)
                editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
              }

              val handler = object : CodeCompletionHandlerBase(CompletionType.BASIC, false, false, true) {
                @Suppress("DEPRECATION")
                override fun completionFinished(indicator: CompletionProgressIndicator, hasModifiers: Boolean) {
                  super.completionFinished(indicator, hasModifiers)
                  lookupItems = indicator.lookup!!.items
                }

                override fun isTestingMode() = true
              }
              handler.invokeCompletion(project, editor, 1)

            }, null, null, editor.document)

            val lookup = LookupManager.getActiveLookup(editor)
            if (lookup != null && lookup is LookupImpl) {
              ScrollingUtil.moveUp(lookup.list, 0)
              lookup.refreshUi(false, false)
              lookupItems = lookup.items
              lookup.hideLookup(true)
            }

            return lookupItems
          }

          val timeStart = System.currentTimeMillis()

          val lookupItems = getLookupItems()
          if (lookupItems != null) {
            result = lookupItems.indexOfFirst { it.lookupString == word }
            total = lookupItems.size
          }

          completionTime.cnt += 1
          completionTime.time += System.currentTimeMillis() - timeStart
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }, ModalityState.NON_MODAL)

      return Pair(result, total)
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
    e.presentation.text = "Completion Quality Statistics"
  }
}

class CompletionQualityDialog(project: Project, editor: Editor?) : DialogWrapper(project) {
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
      when {
        (ft1 == null) -> 1
        (ft2 == null) -> -1
        else -> ft1.description.compareTo(ft2.description, ignoreCase = true)
      }
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
  val id = "${path}:${offset}".hashCode()
}

private data class CompletionStats(val timestamp: Long) {
  var finished: Boolean = false
  val completions = arrayListOf<Completion>()
  var totalFiles = 0
}


private val LOG = Logger.getInstance(CompletionQualityStatsAction::class.java)