// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.impl

import com.intellij.injected.editor.DocumentWindow
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.refactoring.rename.api.FileOperation
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
  val filesToRename: List<Pair<VirtualFile, String>>,
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
      DocumentUtil.executeInBulk(document) {
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
      if (parentFile != virtualFile.parent) {
        virtualFile.move(this, parentFile)
      }
      val newFileName: String = path.fileName.toString()
      if (virtualFile.name != newFileName) {
        virtualFile.rename(this, newFileName)
      }
    }

    for ((virtualFile: VirtualFile, newName: String) in filesToRename) {
      if (!virtualFile.isValid) {
        LOG.warn("Cannot apply rename patch: invalid file to rename. File: $virtualFile")
        continue
      }
      virtualFile.rename(this, newName)
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
          filesToRename = left.filesToRename + right.filesToRename,
          documentModifications = left.documentModifications + right.documentModifications
        )
      }
    }

    fun createFileUpdates(fileOperations: Collection<FileOperation>): FileUpdates {
      ApplicationManager.getApplication().assertReadAccessAllowed()

      val filesToAdd = ArrayList<Pair<Path, CharSequence>>()
      val filesToMove = ArrayList<Pair<VirtualFile, Path>>()
      val filesToRemove = ArrayList<VirtualFile>()
      val filesToRename = ArrayList<Pair<VirtualFile, String>>()
      val fileModifications = ArrayList<Pair<RangeMarker, CharSequence>>()

      loop@
      for (fileOperation: FileOperation in fileOperations) {
        when (fileOperation) {
          is FileOperation.Add -> filesToAdd += Pair(fileOperation.path, fileOperation.content)
          is FileOperation.Move -> filesToMove += Pair(fileOperation.file, fileOperation.path)
          is FileOperation.Remove -> filesToRemove += fileOperation.file
          is FileOperation.Rename -> filesToRename += Pair(fileOperation.file, fileOperation.newName)
          is FileOperation.Modify -> {
            val document: Document = FileDocumentManager.getInstance().getDocument(fileOperation.file.virtualFile) ?: continue@loop
            for (stringOperation: StringOperation in fileOperation.modifications) {
              val rangeMarker: RangeMarker = document.createRangeMarker(stringOperation.range)
              fileModifications += Pair(rangeMarker, stringOperation.replacement)
            }
          }
        }
      }

      return FileUpdates(filesToAdd, filesToMove, filesToRemove, filesToRename, fileModifications)
    }
  }
}
