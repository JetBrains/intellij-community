// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol.modCommand

import com.intellij.modcommand.ModChooseAction
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCompositeCommand
import com.intellij.modcommand.ModCopyToClipboard
import com.intellij.modcommand.ModCreateFile
import com.intellij.modcommand.ModDeleteFile
import com.intellij.modcommand.ModDisplayMessage
import com.intellij.modcommand.ModEditOptions
import com.intellij.modcommand.ModHighlight
import com.intellij.modcommand.ModLaunchEditorAction
import com.intellij.modcommand.ModMoveFile
import com.intellij.modcommand.ModNavigate
import com.intellij.modcommand.ModNothing
import com.intellij.modcommand.ModOpenUrl
import com.intellij.modcommand.ModRegisterTabOut
import com.intellij.modcommand.ModShowConflicts
import com.intellij.modcommand.ModStartRename
import com.intellij.modcommand.ModStartTemplate
import com.intellij.modcommand.ModUpdateFileText
import com.intellij.modcommand.ModUpdateReferences
import com.intellij.modcommand.ModUpdateSystemOptions
import com.intellij.openapi.util.TextRange
import com.intellij.platform.completion.common.ccLogger

private fun TextRange.toRpc(): RpcTextRange = RpcTextRange(startOffset, endOffset)

/**
 * Converts [ModCommand] to serializable [RpcModCommand].
 *
 * Only a subset of ModCommand types commonly used in completion are supported.
 * Returns `null` for unsupported types, which triggers fallback to backend insertion.
 */
fun ModCommand.toRpc(): RpcModCommand? {
  return when (this) {
    is ModNothing -> RpcNothing

    is ModUpdateFileText -> RpcUpdateFileText(
      filePath = file.path,
      oldText = oldText,
      newText = newText,
      updatedRanges = updatedRanges.map { fragment ->
        RpcUpdateFileText.Fragment(
          offset = fragment.offset(),
          oldLength = fragment.oldLength(),
          newLength = fragment.newLength(),
        )
      },
    )

    is ModNavigate -> RpcNavigate(
      filePath = file.path,
      selectionStart = selectionStart,
      selectionEnd = selectionEnd,
      caret = caret,
    )

    is ModCompositeCommand -> {
      val rpcCommands = commands.mapNotNull { it.toRpc() }
      if (rpcCommands.size != commands.size) {
        // Some command couldn't be serialized, fall back to backend
        null
      }
      else {
        RpcComposite(rpcCommands)
      }
    }

    // Simple types (no VirtualFile)
    is ModCopyToClipboard -> RpcCopyToClipboard(content)

    is ModDisplayMessage -> RpcDisplayMessage(
      messageText = messageText,
      kind = when (kind) {
        ModDisplayMessage.MessageKind.INFORMATION -> RpcDisplayMessage.MessageKind.INFORMATION
        ModDisplayMessage.MessageKind.ERROR -> RpcDisplayMessage.MessageKind.ERROR
      },
    )

    is ModLaunchEditorAction -> RpcLaunchEditorAction(actionId, optional)

    is ModOpenUrl -> RpcOpenUrl(url)

    // VirtualFile-based types
    is ModDeleteFile -> RpcDeleteFile(file.path)

    is ModHighlight -> RpcHighlight(
      filePath = file.path,
      highlights = highlights.map { h ->
        RpcHighlight.HighlightInfo(
          startOffset = h.range().startOffset,
          endOffset = h.range().endOffset,
          attributesKeyExternalName = h.attributesKey().externalName,
          hideByTextChange = h.hideByTextChange(),
        )
      },
    )

    is ModRegisterTabOut -> RpcRegisterTabOut(file.path, rangeStart, rangeEnd, target)

    is ModUpdateReferences -> RpcUpdateReferences(
      filePath = file.path,
      oldText = oldText,
      oldRange = oldRange.toRpc(),
      newRange = newRange.toRpc(),
    )

    // Complex types
    is ModCreateFile -> RpcCreateFile(
      parentPath = file.parent.path,
      fileName = file.name,
      isDirectory = file.isDirectory,
      content = when (val c = content) {
        is ModCreateFile.Directory -> RpcCreateFile.FileContent.Empty
        is ModCreateFile.Text -> RpcCreateFile.FileContent.Text(c.text())
        is ModCreateFile.Binary -> RpcCreateFile.FileContent.Binary(c.bytes())
      },
    )

    is ModMoveFile -> RpcMoveFile(
      sourceFilePath = file.path,
      targetParentPath = targetFile.parent.path,
      targetFileName = targetFile.name,
    )

    is ModStartRename -> RpcStartRename(
      filePath = file.path,
      range = symbolRange.range().toRpc(),
      nameIdentifierRange = symbolRange.nameIdentifierRange()?.toRpc(),
      nameSuggestions = nameSuggestions,
    )

    is ModUpdateSystemOptions -> RpcUpdateSystemOptions(
      options = options.map { opt ->
        RpcUpdateSystemOptions.ModifiedOption(
          bindId = opt.bindId(),
          oldValue = opt.oldValue()?.toString(),
          newValue = opt.newValue()?.toString(),
        )
      },
    )

    // Unsupported types - contain closures/PsiElement and cannot be serialized
    is ModChooseAction,
    is ModEditOptions<*>,
    is ModShowConflicts,
    is ModStartTemplate,
      -> {
      ccLogger.info("Unsupported ModCommand type for RPC serialization: ${this::class.simpleName}")
      null
    }
  }
}
