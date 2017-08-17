/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.console

import com.intellij.execution.console.ConsoleHistoryModel.TextWithOffset
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.AtomicFieldUpdater
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.containers.ContainerUtil
import kotlinx.collections.immutable.*

/**
 * @author Yuli Fiterman
 */

private val MasterModels = ConcurrentFactoryMap.createMap<String, MasterModel>({
  MasterModel()
}, {
  ContainerUtil.createConcurrentWeakValueMap()
})

fun createModel(persistenceId: String, console: LanguageConsoleView): ConsoleHistoryModel {


  val masterModel: MasterModel = MasterModels[persistenceId]!!
  val searchPrefixTracker = SearchPrefixTracker(console).apply { install() }
  return PrefixHistoryModel(masterModel) {
    searchPrefixTracker.getPrefixFromEditor()
  }

}

private class SearchPrefixTracker(private val console: LanguageConsoleView) {
  private var lastFirstCaretPosition = 0

  fun install() {
    val listener = object : CaretListener {
      override fun caretPositionChanged(e: CaretEvent) {
        if (e.oldPosition.line == 0) {
          lastFirstCaretPosition = e.oldPosition.column
        }
      }
    }
    val consoleEditor = console.consoleEditor
    consoleEditor.caretModel.addCaretListener(listener)
    Disposer.register(console, Disposable {
      consoleEditor.caretModel.removeCaretListener(listener)
    })
  }

  fun getPrefixFromEditor(): String {
    val editor = console.currentEditor
    val carretOffset = editor.caretModel.offset
    val document = editor.document
    val lineNumber = document.getLineNumber(carretOffset)
    if (lineNumber == 0) {
      return document.getText(TextRange(0, carretOffset))
    }

    val offsetInLine: Int = document.getLineEndOffset(0) - document.getLineStartOffset(0)
    return document.getText(TextRange(0, Math.min(offsetInLine, lastFirstCaretPosition)))

  }

}

private class PrefixHistoryModel constructor(private val masterModel: MasterModel, private val getPrefixFn: () -> String) : ConsoleHistoryModel by masterModel {

  var userContent: String = ""
  override fun setContent(userContent: String) {
    this.userContent = userContent
  }

  private var myEntries: ImmutableList<String>? = null
  private var index: Int = 0


  init {
    resetIndex()
  }

  override fun resetEntries(entries: MutableList<String>) {
    masterModel.resetEntries(entries)
    resetIndex()
  }

  override fun addToHistory(statement: String?) {
    if (statement.isNullOrEmpty()) {
      return
    }
    masterModel.addToHistory(statement)
    resetIndex()
  }


  override fun removeFromHistory(statement: String?) {
    if (statement.isNullOrEmpty()) {
      return
    }
    masterModel.removeFromHistory(statement)
    resetIndex()
  }

  private fun resetIndex() {
    myEntries = null
    index = 0
  }


  override fun getHistoryNext(): TextWithOffset? {
    val entries: ImmutableList<String> = myEntries ?: masterModel.entriesSnap.apply { index = this.size }
    if (index <= 0) {
      return null
    }

    val searchPrefix = getPrefixFn()
    val indexOfLast = entries.subList(0, index).indexOfLast { it.startsWith(searchPrefix) || searchPrefix.isEmpty() }
    if (indexOfLast == -1) {
      return null
    }
    myEntries = entries
    index = indexOfLast
    return TextWithOffset(entries[index], searchPrefix.length)
  }


  override fun getHistoryPrev(): TextWithOffset? {
    val entries: ImmutableList<String> = myEntries ?: return null
    val searchPrefix = getPrefixFn()
    val prevOffset = entries.subList(index + 1, entries.size).indexOfFirst { it.startsWith(searchPrefix) || searchPrefix.isEmpty() }
    return if (prevOffset != -1) {
      index += prevOffset + 1
      TextWithOffset(entries[index], searchPrefix.length)
    }
    else if (userContent.startsWith(searchPrefix) || searchPrefix.isEmpty()) {
      myEntries = null
      index = 0
      TextWithOffset(userContent, searchPrefix.length)

    }
    else {
      null
    }
  }

  override fun hasHistory(): Boolean = myEntries != null
}

private class MasterModel : ConsoleHistoryModel, EntriesWithPositionHolder() {
  override fun setContent(userContent: String) = throw IllegalStateException("Should not be invoked")

  val entriesSnap: ImmutableList<String>
    get() = myState.entries


  override fun resetEntries(ent: MutableList<String>) {
    updateAtomically { state ->
      val newEntries = ent.toImmutableList()
      return@updateAtomically State(newEntries, newEntries.size)
    }
  }


  override fun addToHistory(statement: String?) {
    val stmt = statement ?: return

    updateAtomically { state ->
      val maxHistorySize = maxHistorySize
      val entries = myState.entries
      var newEntries = entries - stmt
      if (newEntries.size >= maxHistorySize) {
        newEntries = newEntries.removeAt(0)
      }
      newEntries = newEntries + stmt

      return@updateAtomically State(newEntries, newEntries.size)
    }
    incModificationCount()
  }


  override fun removeFromHistory(statement: String?) {
    val stmt = statement ?: return
    updateAtomically { state ->
      val entries = myState.entries
      val newEntries = entries - stmt
      return@updateAtomically if (newEntries !== entries) {
        State(newEntries, newEntries.size)
      }
      else {
        state
      }
    }
    incModificationCount()
  }

  override fun getMaxHistorySize(): Int = UISettings.instance.consoleCommandHistoryLimit

  override fun getEntries(): List<String> = myState.entries.toList()

  override fun isEmpty(): Boolean = myState.entries.isEmpty()

  override fun getHistorySize(): Int = myState.entries.size

  override fun getHistoryNext(): TextWithOffset? {
    return updateAtomically { state ->
      val (entries, index) = state
      return@updateAtomically if (index >= 0) {
        State(entries, index - 1)
      }
      else {
        State(entries, index)
      }
    }.currentEntry()?.defaultOffset()
  }

  override fun getHistoryPrev(): TextWithOffset? {
    return updateAtomically { state ->
      val (entries, index) = state
      return@updateAtomically if (index <= entries.size - 1) {
        State(entries, index + 1)
      }
      else {
        State(entries, index)
      }
    }.currentEntry()?.defaultOffset()

  }

  override fun hasHistory(): Boolean {
    val (entries, index) = myState
    return index < entries.size - 1

  }


  override fun prevOnLastLine(): Boolean = true

  override fun getCurrentIndex(): Int = myState.index


}

private fun String.defaultOffset() = TextWithOffset(this, -1)

private open class EntriesWithPositionHolder : SimpleModificationTracker() {
  protected data class State(val entries: ImmutableList<String>, val index: Int)

  protected fun State.currentEntry(): String? {
    val (entries, index) = this
    return entries.getOrNull(index)
  }

  @Volatile
  protected var myState = State(immutableListOf(), -1)

  protected inline fun updateAtomically(fn: (State) -> State): State {
    var newState: State
    do {
      val currentState = myState
      newState = fn(currentState)
    }
    while (!updater.compareAndSet(this, currentState, newState))
    return newState
  }

  companion object {
    val updater = AtomicFieldUpdater.forFieldOfType(EntriesWithPositionHolder::class.java, State::class.java)
  }
}