// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.OptionTag
import java.nio.file.Path

/**
 * Remembers the files the user opened from an external source: the system file manager,
 * the command line, a protocol URI, or drag and drop. Only such files are safe-mode
 * candidates (see [TrustedFiles]); an IDE-internal file (a scratch, a console, the custom
 * VM options file) is never marked and stays trusted.
 *
 * The state is application-level, so a marked file stays a safe-mode candidate after
 * a restart and after a reopen from Recent Files. The list is capped: the oldest entry
 * is evicted first. A mark of a file under an explicitly trusted location is kept:
 * when the user revokes the trust, the file returns to the safe mode.
 */
@State(name = "ExternallyOpenedFiles",
       storages = [Storage(value = "externally-opened-files.xml", roamingType = RoamingType.DISABLED)])
internal class ExternallyOpenedFiles : SerializablePersistentStateComponent<ExternallyOpenedFiles.State>(State()) {
  companion object {
    fun getInstance(): ExternallyOpenedFiles = service()

    private const val MAX_ENTRIES = 100
  }

  data class State(
    @JvmField
    @field:OptionTag("EXTERNALLY_OPENED_FILE_PATHS")
    val paths: List<String> = emptyList(),
  ) {
    /** A lookup view of [paths]. Transient for the same reason as `TrustedPaths.State.trustedState`. */
    @delegate:Transient
    val pathSet: Set<String> by lazy { paths.toSet() }
  }

  fun isMarked(path: Path): Boolean = state.pathSet.contains(path.toString())

  /**
   * Marks [path] as opened from an external source. A repeated mark moves the entry to the fresh end.
   * Returns `true` when the size cap evicted the oldest entry. The evicted path is unmarked now,
   * so the caller must recompute the cached trust verdicts.
   */
  fun mark(path: Path): Boolean {
    val pathString = path.toString()
    var evicted = false
    updateState { state ->
      val paths = buildList {
        addAll(state.paths)
        remove(pathString)
        add(pathString)
      }
      evicted = paths.size > MAX_ENTRIES
      State(paths.takeLast(MAX_ENTRIES))
    }
    return evicted
  }
}
