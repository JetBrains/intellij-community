// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.merge.external.AutomaticExternalMergeTool
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a handler for managing merge requests in the merge conflict resolution process.
 *
 * Useful if you need to know how exactly a merge request will be solved.
 *
 * E.g., The same text merge request can be solved in 3 different ways based on the local configuration:
 * - By using the built-in IntelliJ merge tool
 * - By using an external merge tool set by the user (e.g., BCompare)
 * - By using an installed plugin
 */
@ApiStatus.Internal
sealed interface MergeRequestHandler {

  object BuiltInHandler : MergeRequestHandler

  data class ExtensionBasedHandler(val plugin: AutomaticExternalMergeTool) : MergeRequestHandler

  data class UserConfiguredExternalToolHandler(val tool: ExternalTool) : MergeRequestHandler {
    interface ExternalTool
  }
}
