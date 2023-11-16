// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation.target

import com.intellij.navigation.finder.LocationInFile
import com.intellij.navigation.finder.PsiElementFinder
import com.intellij.navigation.finder.SelectionFinder
import com.intellij.navigation.finder.VirtualFileFinder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiElement
import com.intellij.util.PsiNavigateUtil
import com.intellij.util.containers.ComparatorUtil.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

typealias LocationToOffsetConverter = (LocationInFile, Editor) -> Int

class ReferenceWithinProject(
    private val project: Project,
    private val parameters: Map<String, String>,
    private val locationToOffset: LocationToOffsetConverter = Companion::locationToOffset
) {
  private companion object {
    fun locationToOffset(locationInFile: LocationInFile, editor: Editor): Int {
      return editor.logicalPositionToOffset(
          LogicalPosition(locationInFile.line, locationInFile.column))
    }
  }

  private val selections: List<Pair<LocationInFile, LocationInFile>> by lazy {
    when (val result = SelectionFinder().find(parameters)) {
      is SelectionFinder.FindResult.Success -> result.selections
      is SelectionFinder.FindResult.Error -> emptyList()
    }
  }

  private suspend fun convertLocationToOffset(locationInFile: LocationInFile, editor: Editor): Int {
    return withContext(Dispatchers.EDT) { max(locationToOffset(locationInFile, editor), 0) }
  }

  suspend fun navigate(): String? {
    val psiElementFindResult = PsiElementFinder().find(project, parameters)
    if (psiElementFindResult is PsiElementFinder.FindResult.Success) {
      navigateToPsiElement(psiElementFindResult.psiElement)
    }

    val virtualFileFindResult = VirtualFileFinder().find(project, parameters)
    if (virtualFileFindResult is VirtualFileFinder.FindResult.Success) {
      navigateToVirtualFile(virtualFileFindResult.virtualFile, virtualFileFindResult.locationInFile)
    }

    return null
  }

  private suspend fun navigateToPsiElement(psiElement: PsiElement) {
    withContext(Dispatchers.EDT) { PsiNavigateUtil.navigate(psiElement) }
    makeSelectionsVisible()
  }

  private suspend fun navigateToVirtualFile(
      virtualFile: VirtualFile,
      locationInFile: LocationInFile
  ) {
    val textEditor =
        withContext(Dispatchers.EDT) {
          FileEditorManager.getInstance(project)
              .openFile(virtualFile, true)
              .filterIsInstance<TextEditor>()
              .firstOrNull()
        } ?: return
    performEditorAction(textEditor, locationInFile)
  }

  private suspend fun performEditorAction(textEditor: TextEditor, locationInFile: LocationInFile) {
    withContext(Dispatchers.EDT) {
      val editor = textEditor.editor
      editor.caretModel.removeSecondaryCarets()
      editor.caretModel.moveToOffset(convertLocationToOffset(locationInFile, editor))
      editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
      editor.selectionModel.removeSelection()
      IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
    }
    makeSelectionsVisible()
  }

  private suspend fun makeSelectionsVisible() {
    withContext(Dispatchers.EDT) {
      val editor = FileEditorManager.getInstance(project).selectedTextEditor
      selections.forEach {
        editor
            ?.selectionModel
            ?.setSelection(
                convertLocationToOffset(it.first, editor),
                convertLocationToOffset(it.second, editor))
      }
    }
  }
}
