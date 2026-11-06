// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.eclipse.lsp4j.Location
import org.jetbrains.annotations.ApiStatus

abstract class Lsp4jService {

  /**
   * Extracts a list of LSP [org.eclipse.lsp4j.Location] objects from command arguments.
   *
   * This function is useful for parsing location data from [LSP command arguments][org.eclipse.lsp4j.Command.arguments]
   * (e.g., for "showReferences" commands). It expects the arguments to follow the pattern:
   * `[uri, position, locations[]]` where locations is a JSON array at index 2.
   *
   * Expected argument structure:
   * ```json
   * [
   *   "file:///path/to/file1.ext",
   *   { "line": 9, "character": 7 },
   *   [
   *     {
   *       "uri": "file:///path/to/file2.ext",
   *       "range": {
   *         "start": { "line": 13, "character": 10 },
   *         "end": { "line": 13, "character": 15 }
   *       }
   *     },
   *     {
   *       "uri": "file:///path/to/file3.ext",
   *       "range": {
   *         "start": { "line": 25, "character": 0 },
   *         "end": { "line": 33, "character": 1 }
   *       }
   *     }
   *   ]
   * ]
   * ```
   *
   * @param arguments the command arguments list, typically [org.eclipse.lsp4j.Command.arguments]
   * @return a list of [org.eclipse.lsp4j.Location] objects extracted from the arguments, or null if extraction fails
   * @see org.eclipse.lsp4j.Command.arguments
   */
  abstract fun extractLocationsFromJson(arguments: List<Any>?): List<Location>?

  /**
   * The text a snippet edit inserts when it does not go live;
   * a tab stop or placeholder is its default (a mirror repeats it), `$0` and a variable without a default are empty.
   */
  @ApiStatus.Internal
  abstract fun getSnippetText(snippet: String): String

  /**
   * Replaces the text at [range] of the [editor] document with the live template of [snippet].
   */
  @ApiStatus.Internal
  abstract fun startSnippet(editor: Editor, range: TextRange, snippet: String)

  companion object {
    @JvmStatic
    fun getInstance(): Lsp4jService = ApplicationManager.getApplication().service()
  }
}