// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol.modCommand

import com.intellij.modcommand.FutureVirtualFile
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCopyToClipboard
import com.intellij.modcommand.ModCreateFile
import com.intellij.modcommand.ModDeleteFile
import com.intellij.modcommand.ModDisplayMessage
import com.intellij.modcommand.ModHighlight
import com.intellij.modcommand.ModLaunchEditorAction
import com.intellij.modcommand.ModMoveFile
import com.intellij.modcommand.ModNavigate
import com.intellij.modcommand.ModOpenUrl
import com.intellij.modcommand.ModRegisterTabOut
import com.intellij.modcommand.ModStartRename
import com.intellij.modcommand.ModUpdateFileText
import com.intellij.modcommand.ModUpdateReferences
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.completion.common.ccLogger

/**
 * Converts [RpcModCommand] back to [ModCommand] on the frontend.
 *
 * Only a subset of ModCommand types commonly used in completion are supported.
 * Returns `null` for unsupported types or if required files cannot be found.
 */
fun RpcModCommand.toModCommand(): ModCommand? {
  return when (this) {
    is RpcNothing -> ModCommand.nop()

    is RpcUpdateFileText -> {
      val vFile = findFile(filePath) ?: return logFileNotFound("UpdateFileText", filePath)
      ModUpdateFileText(
        vFile,
        oldText,
        newText,
        updatedRanges.map { fragment ->
          ModUpdateFileText.Fragment(
            fragment.offset,
            fragment.oldLength,
            fragment.newLength,
          )
        },
      )
    }

    is RpcNavigate -> {
      val vFile = findFile(filePath) ?: return logFileNotFound("Navigate", filePath)
      ModNavigate(vFile, selectionStart, selectionEnd, caret)
    }

    is RpcComposite -> {
      val modCommands = commands.mapNotNull { it.toModCommand() }
      if (modCommands.size != commands.size) {
        // Some command couldn't be deserialized
        ccLogger.warn("ModCommandDeserializer: some composite commands could not be deserialized")
        return null
      }
      modCommands.fold(ModCommand.nop()) { acc, cmd -> acc.andThen(cmd) }
    }

    // Simple types (no VirtualFile)
    is RpcCopyToClipboard -> ModCopyToClipboard(content)

    is RpcDisplayMessage -> ModDisplayMessage(
      messageText,
      when (kind) {
        RpcDisplayMessage.MessageKind.INFORMATION -> ModDisplayMessage.MessageKind.INFORMATION
        RpcDisplayMessage.MessageKind.ERROR -> ModDisplayMessage.MessageKind.ERROR
      },
    )

    is RpcLaunchEditorAction -> ModLaunchEditorAction(actionId, optional)

    is RpcOpenUrl -> ModOpenUrl(url)

    // VirtualFile-based types
    is RpcDeleteFile -> {
      val vFile = findFile(filePath) ?: return logFileNotFound("DeleteFile", filePath)
      ModDeleteFile(vFile)
    }

    is RpcHighlight -> {
      val vFile = findFile(filePath) ?: return logFileNotFound("Highlight", filePath)
      val infos = highlights.map { h ->
        ModHighlight.HighlightInfo(
          TextRange(h.startOffset, h.endOffset),
          TextAttributesKey.find(h.attributesKeyExternalName),
          h.hideByTextChange,
        )
      }
      ModHighlight(vFile, infos)
    }

    is RpcRegisterTabOut -> {
      val vFile = findFile(filePath) ?: return logFileNotFound("RegisterTabOut", filePath)
      ModRegisterTabOut(vFile, rangeStart, rangeEnd, target)
    }

    is RpcUpdateReferences -> {
      val vFile = findFile(filePath) ?: return logFileNotFound("UpdateReferences", filePath)
      ModUpdateReferences(vFile, oldText, oldRange.toTextRange(), newRange.toTextRange())
    }

    // Complex types
    is RpcCreateFile -> {
      val parentVFile = findFile(parentPath) ?: return logFileNotFound("CreateFile parent", parentPath)
      val fileType = if (isDirectory) null else parentVFile.fileSystem.findFileByPath("$parentPath/$fileName")?.fileType
      val futureFile = FutureVirtualFile(parentVFile, fileName, fileType)
      val modContent = when (val c = content) {
        RpcCreateFile.FileContent.Empty -> ModCreateFile.Directory()
        is RpcCreateFile.FileContent.Text -> ModCreateFile.Text(c.text)
        is RpcCreateFile.FileContent.Binary -> ModCreateFile.Binary(c.bytes)
      }
      ModCreateFile(futureFile, modContent)
    }

    is RpcMoveFile -> {
      val vFile = findFile(sourceFilePath) ?: return logFileNotFound("MoveFile source", sourceFilePath)
      val targetParent = findFile(targetParentPath) ?: return logFileNotFound("MoveFile target", targetParentPath)
      val futureFile = FutureVirtualFile(targetParent, targetFileName, vFile.fileType)
      ModMoveFile(vFile, futureFile)
    }

    is RpcStartRename -> {
      val vFile = findFile(filePath) ?: return logFileNotFound("StartRename", filePath)
      val symbolRange = ModStartRename.RenameSymbolRange(range.toTextRange(), nameIdentifierRange?.toTextRange())
      ModStartRename(vFile, symbolRange, nameSuggestions)
    }

    is RpcUpdateSystemOptions -> {
      // Cannot properly deserialize - Object values are lost in string conversion
      // Return null to fallback to backend execution
      ccLogger.info("UpdateSystemOptions cannot be deserialized, falling back to backend")
      null
    }
  }
}

private fun findFile(path: String): VirtualFile? =
  VirtualFileManager.getInstance().findFileByUrl("file://$path")

private fun RpcTextRange.toTextRange(): TextRange = TextRange(startOffset, endOffset)

private fun logFileNotFound(commandType: String, path: String): Nothing? {
  ccLogger.warn("ModCommandDeserializer: file not found for $commandType: $path")
  return null
}
