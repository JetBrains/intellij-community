package com.intellij.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.ui.CodeVisionView
import com.intellij.codeInsight.codeVision.ui.model.PlaceholderCodeVisionEntry
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.application
import com.jetbrains.rd.util.first
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import com.jetbrains.rd.util.lifetime.onTermination
import java.awt.event.MouseEvent

val editorLensContextKey = Key<EditorCodeVisionContext>("EditorCodeLensContext")
val codeVisionEntryOnHighlighterKey = Key.create<CodeVisionEntry>("CodeLensEntryOnHighlighter")
val codeVisionEntryMouseEventKey = Key.create<MouseEvent>("CodeVisionEntryMouseEventKey")

val Editor.lensContext: EditorCodeVisionContext?
  get() = getOrCreateCodeVisionContext(this)
val Editor.lensContextOrThrow: EditorCodeVisionContext
  get() = lensContext ?: error("No EditorCodeVisionContext were provided")
val RangeMarker.codeVisionEntryOrThrow: CodeVisionEntry
  get() = getUserData(codeVisionEntryOnHighlighterKey) ?: error("No CodeLensEntry for highlighter $this")


open class EditorCodeVisionContext(
  private val codeVisionHost: CodeVisionHost,
  val editor: Editor
) {
  val outputLifetimes = SequentialLifetimes((editor as EditorImpl).disposable.createLifetime())
  private var frontendResults: List<RangeMarker> = listOf()

  companion object {
    val logger = Logger.getInstance(EditorCodeVisionContext::class.java)
  }

  private var hasPendingLenses = false

  private val submittedGroupings = ArrayList<Pair<TextRange, (Int) -> Unit>>()

  init {
    (editor as EditorImpl).disposable.createLifetime().onTermination {
      frontendResults.forEach { it.dispose() }
    }
  }


  fun notifyPendingLenses() {
    application.assertIsDispatchThread()
    if (!hasPendingLenses)
      logger.trace("Have pending lenses")
    hasPendingLenses = true
  }

  open val hasAnyPendingLenses: Boolean
    get() = hasPendingLenses


  fun setResults(lenses: List<Pair<TextRange, CodeVisionEntry>>) {
    application.assertIsDispatchThread()
    logger.trace("Have new frontend lenses ${lenses.size}")
    frontendResults.forEach { it.dispose() }
    frontendResults = lenses.mapNotNull { (range, entry) ->
      if(!range.isValidFor(editor.document)) return@mapNotNull null
      editor.document.createRangeMarker(range).apply {
        putUserData(codeVisionEntryOnHighlighterKey, entry)
      }
    }
    resubmitThings()
    hasPendingLenses = false
  }

  fun discardPending(){
    hasPendingLenses = false
  }


  protected fun resubmitThings() {
    val viewService = ServiceManager.getService(
      editor.project!!,
      CodeVisionView::class.java
    )

    viewService.runWithReusingLenses {
      val lifetime = outputLifetimes.next()
      val mergedLenses = getValidResult().groupBy { editor.document.getLineNumber(it.startOffset) }


      submittedGroupings.clear()
      mergedLenses.forEach { (_, lineLenses) ->
        if (lineLenses.isEmpty())
          return@forEach

        val lastFilteredLineLenses = lineLenses.groupBy { it.codeVisionEntryOrThrow.providerId }.map { it.value.last() }

        val groupedLenses = lastFilteredLineLenses.groupBy { codeVisionHost.getAnchorForEntry(it.codeVisionEntryOrThrow) }

        val anchoringRange = groupedLenses.first().value.first()
        val range = TextRange(anchoringRange.startOffset, anchoringRange.endOffset)
        val handlerLambda = viewService.addCodeLenses(lifetime,
                                                      editor,
                                                      range,
                                                      groupedLenses.map {
                                                        it.key to it.value.map { it.codeVisionEntryOrThrow }.sortedBy {
                                                          codeVisionHost.getPriorityForEntry(it)
                                                        }
                                                      }.associate { it })
        val moreRange = TextRange(editor.document.getLineStartOffset(editor.document.getLineNumber(range.startOffset)), range.endOffset)
        submittedGroupings.add(moreRange to handlerLambda)
      }

      submittedGroupings.sortBy { it.first.startOffset }
    }
  }

  open fun clearLenses() {
    setResults(emptyList())
  }

  protected open fun getValidResult() = frontendResults.asSequence().filter { it.isValid }

  protected fun TextRange.isValidFor(document: Document): Boolean {
    return this.startOffset >= 0 && this.endOffset <= document.textLength
  }

  fun invokeMoreMenu(caretOffset: Int) {
    val selectedLens = submittedGroupings.binarySearchBy(caretOffset) { it.first.startOffset }.let { if (it < 0) -(it + 1) - 1 else it }
    if (selectedLens < 0 || selectedLens > submittedGroupings.lastIndex)
      return
    submittedGroupings[selectedLens].second(caretOffset)
  }

  fun hasProviderCodeVision(id: String): Boolean {
    return frontendResults.mapNotNull { it.getUserData(codeVisionEntryOnHighlighterKey) }.any { it.providerId == id }
  }

  open fun hasOnlyPlaceholders(): Boolean{
    return frontendResults.all { it.getUserData(codeVisionEntryOnHighlighterKey) is PlaceholderCodeVisionEntry }
  }
}


private fun getOrCreateCodeVisionContext(editor: Editor): EditorCodeVisionContext? {
  val context = editor.getUserData(editorLensContextKey)
  if (context != null) return context

  val newContext = editor.project!!.service<CodeVisionContextProvider>().createCodeVisionContext(editor)
  editor.putUserData(editorLensContextKey, newContext)
  return newContext
}

internal fun List<Pair<String, CodeVisionProvider<*>>>.getTopSortedIdList(): List<String> {
  val fakeFirstNode = "!first!"
  val fakeLastNode = "!last!"

  val nodesAfter = HashMap<String, ArrayList<String>>()

  nodesAfter.getOrPut(fakeFirstNode) { ArrayList() }.add(fakeLastNode)

  this.forEach { (k, v) ->
    v.relativeOrderings.forEach {
      when (it) {
        is CodeVisionRelativeOrdering.CodeVisionRelativeOrderingBefore -> nodesAfter.getOrPut(k) { ArrayList() }.add(it.id)
        is CodeVisionRelativeOrdering.CodeVisionRelativeOrderingAfter -> nodesAfter.getOrPut(it.id) { ArrayList() }.add(k)
        is CodeVisionRelativeOrdering.CodeVisionRelativeOrderingFirst -> nodesAfter.getOrPut(fakeFirstNode) { ArrayList() }.add(k)
        is CodeVisionRelativeOrdering.CodeVisionRelativeOrderingLast -> nodesAfter.getOrPut(fakeLastNode) { ArrayList() }.add(k)
        else -> error("Unknown node ordering class ${it.javaClass.simpleName}")
      }
    }
  }

  val dfsVisited = HashSet<String>()
  val sortedList = ArrayList<String>()

  fun dfsTopSort(currentId: String) {
    if (!dfsVisited.add(currentId))
      return

    nodesAfter[currentId]?.forEach {
      dfsTopSort(it)
    }

    if (currentId != fakeFirstNode && currentId != fakeLastNode)
      sortedList.add(currentId)
  }

  dfsTopSort(fakeLastNode)
  this.forEach { dfsTopSort(it.first) }
  dfsTopSort(fakeFirstNode)

  sortedList.reverse()

  return sortedList
}

internal fun <T> MutableList<T>.swap(i1: Int, i2: Int) {
  val t = get(i1)
  set(i1, get(i2))
  set(i2, t)
}