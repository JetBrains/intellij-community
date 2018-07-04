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

import com.intellij.execution.console.ConsoleHistoryModel.Entry
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.containers.ContainerUtil
import gnu.trove.TIntStack

/**
 * @author Yuli Fiterman
 */

private val MasterModels = ConcurrentFactoryMap.createMap<String, MasterModel>(
  {
    MasterModel()
  }, {
    ContainerUtil.createConcurrentWeakValueMap()
  })


private fun assertDispatchThread() = ApplicationManager.getApplication().assertIsDispatchThread()


fun createModel(persistenceId: String, console: LanguageConsoleView): ConsoleHistoryModel {
  val masterModel: MasterModel = MasterModels[persistenceId]!!
  fun getPrefixFromConsole(): String {
    val caretOffset = console.consoleEditor.caretModel.offset
    return console.editorDocument.getText(TextRange.create(0, caretOffset))
  }
  return PrefixHistoryModel(masterModel, ::getPrefixFromConsole)
}


private class PrefixHistoryModel constructor(private val masterModel: MasterModel,
                                             private val getPrefixFn: () -> String) : ConsoleHistoryBaseModel by masterModel,
                                                                                      ConsoleHistoryModel {
  var userContent: String = ""
  override fun setContent(userContent: String) {
    this.userContent = userContent
  }

  private var currentIndex: Int? = null
  private var currentEntries: List<String>? = null
  private var prevEntries: TIntStack = TIntStack()
  private var historyPrefix: String = ""

  init {
    resetIndex()
  }

  override fun resetEntries(entries: MutableList<String>) {
    masterModel.resetEntries(entries)
    resetIndex()
  }

  override fun addToHistory(statement: String?) {
    assertDispatchThread()
    if (statement.isNullOrEmpty()) {
      return
    }
    masterModel.addToHistory(statement)
    resetIndex()
  }

  override fun removeFromHistory(statement: String?) {
    assertDispatchThread()
    if (statement.isNullOrEmpty()) {
      return
    }
    masterModel.removeFromHistory(statement)
    resetIndex()
  }

  private fun resetIndex() {
    currentIndex = null
    currentEntries = null
    prevEntries.clear()
    historyPrefix = ""
  }

  override fun getHistoryNext(): Entry? {
    val entries = currentEntries ?: masterModel.entries
    val offset = currentIndex ?: entries.size
    if (offset <= 0) {
      return null
    }
    if (currentIndex == null) {
      historyPrefix = getPrefixFn()
    }
    val res = entries.withIndex().findLast { it.index < offset && it.value.startsWith(historyPrefix) } ?: return null

    if (currentEntries == null) {
      currentEntries = entries
    }
    currentIndex?.let { prevEntries.push(it) }
    currentIndex = res.index
    return createEntry(res.value)
  }

  override fun getHistoryPrev(): Entry? {
    val entries = currentEntries ?: return null
    return if (prevEntries.size() > 0) {
      val index = prevEntries.pop()
      currentIndex = index
      createEntry(entries[index])
    }
    else {
      resetIndex()
      createEntry(userContent)
    }
  }

  private fun createEntry(prevEntry: String): Entry = Entry(prevEntry, prevEntry.length)

  override fun getCurrentIndex(): Int = currentIndex ?: entries.size-1

  override fun prevOnLastLine(): Boolean = true

  override fun hasHistory(): Boolean = currentEntries != null
}

private class MasterModel(private val modTracker: SimpleModificationTracker = SimpleModificationTracker()) : ConsoleHistoryBaseModel, ModificationTracker by modTracker {

  @Volatile
  private var entries: MutableList<String> = mutableListOf()

  @Suppress("UNCHECKED_CAST")
  override fun getEntries(): MutableList<String> = entries.toMutableList()

  override fun resetEntries(ent: List<String>) {
    entries = ent.toMutableList()
  }

  override fun addToHistory(statement: String?) {
    if (statement == null) return
    entries.remove(statement)
    entries.add(statement)
    if (entries.size >= maxHistorySize) {
      entries.removeAt(0)
    }
    modTracker.incModificationCount()
  }

  override fun removeFromHistory(statement: String?) {
    if (statement == null) return
    entries.remove(statement)
    modTracker.incModificationCount()
  }

  override fun getMaxHistorySize(): Int = UISettings.instance.consoleCommandHistoryLimit

  override fun isEmpty(): Boolean = entries.isEmpty()

  override fun getHistorySize(): Int = entries.size
}
