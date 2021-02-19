// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks.actions

import com.intellij.ide.bookmarks.Bookmark
import com.intellij.ide.bookmarks.BookmarkManager
import com.intellij.ide.bookmarks.BookmarkType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.awt.RelativePoint
import java.awt.Component

internal data class BookmarkContext(val project: Project, val file: VirtualFile, val editor: Editor?, val line: Int) {
  val manager: BookmarkManager by lazy { BookmarkManager.getInstance(project) }

  val bookmark: Bookmark?
    get() = manager.findBookmark(file, line)

  fun setType(type: BookmarkType) {
    val bookmark = manager.findBookmark(file, line)
    when {
      bookmark == null -> {
        when (editor != null) {
          true -> manager.addEditorBookmark(editor, line)
          else -> manager.addTextBookmark(file, line, "")
        }
        manager.findBookmark(file, line)?.let {
          if (BookmarkType.DEFAULT != type) {
            manager.setMnemonic(it, type.mnemonic)
          }
        }
      }
      bookmark.mnemonic == type.mnemonic -> manager.removeBookmark(bookmark)
      else -> manager.setMnemonic(bookmark, type.mnemonic)
    }
  }

  internal fun getPointOnGutter(component: Component?) =
    if (editor == null || line < 0) null
    else getGutter(component)?.let {
      RelativePoint(it, editor.logicalPositionToXY(LogicalPosition(line, 0)).apply {
        x = it.iconAreaOffset
        y += editor.lineHeight
      })
    }

  private fun getGutter(component: Component?) =
    component as? EditorGutterComponentEx ?: when (Registry.`is`("ide.bookmark.mnemonic.chooser.always.above.gutter")) {
      true -> editor?.gutter as? EditorGutterComponentEx
      else -> null
    }
}

internal val DataContext.context: BookmarkContext?
  get() {
    val project = getData(CommonDataKeys.PROJECT) ?: return null
    val editor = getData(CommonDataKeys.EDITOR) ?: getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE)
    if (editor != null) {
      if (editor.isOneLineMode) return null
      val line = getData(EditorGutterComponentEx.LOGICAL_LINE_AT_CURSOR) ?: editor.caretModel.logicalPosition.line
      val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
      return if (file is LightVirtualFile) null else BookmarkContext(project, file, editor, line)
    }
    val psiElement = getData(PlatformDataKeys.PSI_ELEMENT)
    val elementFile = PsiUtilCore.getVirtualFile(psiElement?.containingFile)
    if (psiElement != null && psiElement !is PsiCompiledElement && elementFile != null) {
      if (elementFile is LightVirtualFile) return null
      val line = FileDocumentManager.getInstance().getDocument(elementFile)
                   ?.getLineNumber(psiElement.textOffset) ?: -1
      return BookmarkContext(project, elementFile, null, line)
    }
    val file = getData(PlatformDataKeys.VIRTUAL_FILE)
    if (file != null && file !is LightVirtualFile) {
      return BookmarkContext(project, file, null, -1)
    }
    return null
  }
