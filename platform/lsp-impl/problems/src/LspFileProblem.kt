// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.problems

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.impl.problems.LspProjectErrorsModel.Key
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.platform.lsp.util.messageIfStringOrEmpty
import com.intellij.pom.Navigatable
import org.eclipse.lsp4j.Diagnostic
import org.jetbrains.annotations.ApiStatus

/**
 * One row of the Project Errors tab: an error [client] pushed for [file] (a model [Key] plus its current [diagnostic]).
 * - The text is the first line of the message; the description is the whole message.
 * - [line] and [column] are the range start as the server sent it. A click navigates to the offset in the document.
 * - Equal rows have the same [provider], [client], [key] and [file]: an update with another diagnostic is the same row
 *   (`ProblemsCollector.problemUpdated` swaps it).
 */
@ApiStatus.Internal
class LspFileProblem(
  override val provider: ProblemsProvider,
  val client: LspClient,
  val key: Key,
  val diagnostic: Diagnostic,
  override val file: VirtualFile,
) : FileProblem, Navigatable {
  override val text: String get() = text(diagnostic)

  override val description: String get() = diagnostic.messageIfStringOrEmpty

  override val line: Int get() = diagnostic.range.start.line

  override val column: Int get() = diagnostic.range.start.character

  override fun canNavigate(): Boolean = file.isValid

  override fun canNavigateToSource(): Boolean = canNavigate()

  override fun navigate(requestFocus: Boolean) {
    val document = FileDocumentManager.getInstance().getDocument(file)
    val offset = document?.let { getOffsetInDocument(it, diagnostic.range.start) }
    val descriptor = offset?.let { OpenFileDescriptor(provider.project, file, it) }
                     ?: OpenFileDescriptor(provider.project, file, line, column)
    descriptor.navigate(requestFocus)
  }

  override fun equals(other: Any?): Boolean =
    other is LspFileProblem && other.provider == provider && other.client == client && other.key == key && other.file == file

  override fun hashCode(): Int = (provider.hashCode() * 31 + client.hashCode()) * 31 + key.hashCode()

  override fun toString(): String = "LspFileProblem(${client.descriptor.presentableName}, ${file.path}:${line + 1}, $text)"

  companion object {
    /** The one-line text of [diagnostic]: the first non-blank line of its message. */
    fun text(diagnostic: Diagnostic): String {
      val message = diagnostic.messageIfStringOrEmpty
      return message.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: message
    }
  }
}
