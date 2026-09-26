// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.console.history

import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CommandHistory {
    class Entry(
        val entryText: String,
        val rangeInHistoryDocument: TextRange
    )

    private val entries = arrayListOf<Entry>()
    var processedEntriesCount: Int = 0
        private set

    val listeners = arrayListOf<HistoryUpdateListener>()

    operator fun get(i: Int) = entries[i]

    fun addEntry(entry: Entry) {
        entries.add(entry)
        listeners.forEach { it.onNewEntry(entry) }
    }

    fun lastUnprocessedEntry(): Entry? = if (processedEntriesCount < size) {
        get(processedEntriesCount)
    } else {
        null
    }

    fun entryProcessed() {
        processedEntriesCount++
    }

    val size: Int get() = entries.size
}

@ApiStatus.Internal
interface HistoryUpdateListener {
    fun onNewEntry(entry: CommandHistory.Entry)
}