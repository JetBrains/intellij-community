// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.model.error

import com.intellij.platform.pluginGraph.ContentModuleName
import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle
import java.nio.file.Path

/**
 * Type of file change detected.
 */
enum class FileChangeType {
  /** File will be created */
  CREATE,
  /** File content will be modified */
  MODIFY,
  /** File will be deleted */
  DELETE,
}

/**
 * Represents a file difference detected during generation.
 * Implements [ValidationError] so diffs can be handled uniformly with other validation issues.
 *
 * Field semantics match [com.intellij.platform.testFramework.core.FileComparisonFailedError]:
 * - [expectedContent] = what we WANT (generated/new content)
 * - [actualContent] = what we HAVE (current content on disk)
 */
data class FileDiff(
  override val context: String,
  @JvmField val path: Path,
  /** The generated/desired content (what the file should contain) */
  @JvmField val expectedContent: String,
  /** The current content on disk (what the file actually contains) */
  @JvmField val actualContent: String,
  @JvmField val changeType: FileChangeType,
  override val ruleName: String = "FileUpdater",
) : ValidationError {
  override val category: ErrorCategory get() = ErrorCategory.FILE_DIFF

  override fun format(s: AnsiStyle): String = context  // FileDiff uses context directly as the message
}

/**
 * Suppressible pipeline error that was not suppressed.
 *
 * Unlike other ValidationErrors which are hard failures, this error type
 * represents issues that CAN be suppressed via `suppressions.json` but weren't.
 * The [suppressionKey] allows the error to be suppressed in future runs.
 */
data class UnsuppressedPipelineError(
  override val context: String,
  /** Original pipeline error key (used for suppression matching) */
  @JvmField val errorKey: String,
  /** Human-readable message from the pipeline error */
  @JvmField val message: String,
  /** Error category for this error */
  @JvmField val errorCategory: ErrorCategory,
  /** Module name if available */
  val contentModuleName: ContentModuleName? = null,
  /** File path if available */
  @JvmField val path: Path? = null,
  override val ruleName: String = "PipelineValidation",
) : ValidationError {
  override val category: ErrorCategory get() = errorCategory

  /** This error CAN be suppressed - return the suppression key */
  override val suppressionKey: String get() = errorKey

  override fun format(s: AnsiStyle): String = buildString {
    appendLine("${s.red}${s.bold}Error [${errorCategory}]${s.reset}")
    appendLine()
    appendLine("  ${s.red}*${s.reset} $message")
    if (contentModuleName != null) {
      appendLine("    Module: ${s.bold}${contentModuleName.value}${s.reset}")
    }
    if (path != null) {
      appendLine("    Path: ${s.gray}$path${s.reset}")
    }
    appendLine()
    appendLine("${s.blue}To suppress:${s.reset} Add to suppressedErrors in suppressions.json:")
    appendLine("  ${s.gray}\"$errorKey\"${s.reset}")
    appendLine()
    appendLine("${s.gray}[Rule: $ruleName]${s.reset}")
    appendLine()
  }
}
