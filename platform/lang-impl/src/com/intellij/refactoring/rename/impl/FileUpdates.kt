// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.injected.editor.DocumentWindow
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.refactoring.suggested.range
import com.intellij.util.DocumentUtil
import com.intellij.util.io.write
import com.intellij.util.text.StringOperation
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
class FileUpdates(
  val filesToAdd: List<Pair<Path, CharSequence>>,
  val filesToMove: List<Pair<VirtualFile, Path>>,
  val filesToRemove: List<VirtualFile>,
  val documentModifications: List<Pair<RangeMarker, CharSequence>>
) {
  fun doUpdate() {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    for (virtualFile: VirtualFile in filesToRemove) {
      if (!virtualFile.isValid) {
        LOG.warn("Cannot apply rename patch: invalid file to remove. File: $virtualFile")
        continue
      }
      virtualFile.delete(this)
    }

    val byDocument = documentModifications.groupBy { (rangeMarker: RangeMarker, _) ->
      rangeMarker.document
    }
    for ((document: Document, modifications: List<Pair<RangeMarker, CharSequence>>) in byDocument) {
      DocumentUtil.executeInBulk(document, true) {
        for ((rangeMarker: RangeMarker, replacement: CharSequence) in modifications) {
          if (!rangeMarker.isValid) {
            LOG.warn("Cannot apply rename patch: invalid range marker. Document: $document, marker: $rangeMarker")
            continue
          }
          document.replaceString(rangeMarker.startOffset, rangeMarker.endOffset, replacement)
          rangeMarker.dispose()
        }
      }
    }

    for ((virtualFile: VirtualFile, path: Path) in filesToMove) {
      if (!virtualFile.isValid) {
        LOG.warn("Cannot apply rename patch: invalid file to move. File: $virtualFile")
        continue
      }
      val parentPath: Path = path.parent ?: continue
      val parentFile: VirtualFile = VfsUtil.findFile(parentPath, false) ?: continue
      virtualFile.move(this, parentFile)
      val newFileName: String = path.fileName.toString()
      if (virtualFile.name != newFileName) {
        virtualFile.rename(this, newFileName)
      }
    }

    for ((path: Path, content: CharSequence) in filesToAdd) {
      path.write(content)
    }
  }

  fun preview(): Map<VirtualFile, CharSequence> {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val byDocument = documentModifications.groupBy { (rangeMarker: RangeMarker, _) ->
      rangeMarker.document
    }
    val documentOperations = HashMap<Document, MutableList<StringOperation>>(byDocument.size)
    for ((document: Document, modifications: List<Pair<RangeMarker, CharSequence>>) in byDocument) {
      val operations: List<StringOperation> = modifications.mapNotNull { (rangeMarker: RangeMarker, replacement: CharSequence) ->
        rangeMarker.range?.let { range ->
          StringOperation.replace(range, replacement)
        }
      }
      if (document is DocumentWindow) {
        documentOperations.getOrPut(document.delegate) { ArrayList() } += operations.flatMap { operation ->
          val range = operation.range
          document.prepareReplaceString(range.startOffset, range.endOffset, operation.replacement)
        }
      }
      else {
        documentOperations.getOrPut(document) { ArrayList() } += operations
      }
    }
    val fileText = HashMap<VirtualFile, CharSequence>(byDocument.size)
    for ((document, operations) in documentOperations) {
      check(document !is DocumentWindow)
      val virtualFile: VirtualFile = requireNotNull(FileDocumentManager.getInstance().getFile(document))
      fileText[virtualFile] = StringOperation.applyOperations(document.charsSequence, operations)
    }
    return fileText
  }

  companion object {
    internal val LOG: Logger = Logger.getInstance(FileUpdates::class.java)

    fun merge(left: FileUpdates?, right: FileUpdates?): FileUpdates? {
      return when {
        left == null -> right
        right == null -> left
        else -> FileUpdates(
          filesToAdd = left.filesToAdd + right.filesToAdd,
          filesToMove = left.filesToMove + right.filesToMove,
          filesToRemove = left.filesToRemove + right.filesToRemove,
          documentModifications = left.documentModifications + right.documentModifications
        )
      }
    }
  }
}
