// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.navigation.*
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.PlatformUtils.*
import com.intellij.util.Urls
import com.intellij.util.concurrency.ThreadingAssertions

internal object CopyTBXReferenceAction {
  private val LOG = Logger.getInstance(CopyTBXReferenceAction::class.java)

  private val IDE_TAGS = mapOf(
    IDEA_PREFIX to "idea",
    IDEA_CE_PREFIX to "idea",
    APPCODE_PREFIX to "appcode",
    CLION_PREFIX to "clion",
    PYCHARM_PREFIX to "pycharm",
    PYCHARM_CE_PREFIX to "pycharm",
    PYCHARM_EDU_PREFIX to "pycharm",
    DATASPELL_PREFIX to "pycharm",
    PHP_PREFIX to "php-storm",
    RUBY_PREFIX to "rubymine",
    WEB_PREFIX to "web-storm",
    RIDER_PREFIX to "rd",
    GOIDE_PREFIX to "goland",
    DBE_PREFIX to "dbe"
  )

  fun createJetBrainsLink(project: Project, elements: List<PsiElement>, editor: Editor?): String? {
    ThreadingAssertions.assertReadAccess()

    val tool = IDE_TAGS[getPlatformPrefix()]
    if (tool == null) {
      LOG.warn("Cannot find TBX tool for IDE: ${getPlatformPrefix()}")
      return null
    }

    val references = elements.asSequence()
      .map { psi -> FqnUtil.elementToFqn(psi, editor)?.let { fqn -> fqn to isFile(psi) } }
      .filterNotNull()
      .map { FileUtil.getLocationRelativeToUserHome(it.first, false) to it.second }
      .toMutableList()
    if (references.isEmpty() && editor != null) {
      val file = PsiDocumentManager.getInstance(project).getCachedPsiFile(editor.document)
      if (file != null) {
        val position = editor.caretModel.logicalPosition
        val path = "${FqnUtil.getFileFqn(file)}:${position.line + 1}:${position.column + 1}"
        references += path to true
      }
    }
    if (references.isEmpty()) {
      return null
    }

    val parameters = LinkedHashMap(mapOf(PROJECT_NAME_KEY to project.name))

    references.forEachIndexed { i, (reference, isFile) ->
      val navigationKey = if (isFile) NavigatorWithinProject.NavigationKeyPrefix.PATH else NavigatorWithinProject.NavigationKeyPrefix.FQN
      val index = parameterIndex(i, references.size)
      parameters += "${navigationKey}${index}" to "$reference"
    }

    if (editor != null) {
      val carets = if (editor.caretModel.supportsMultipleCarets()) editor.caretModel.allCarets else listOf(editor.caretModel.currentCaret)
      val ranges = carets.mapNotNull { caret -> getSelectionRange(editor, caret) }
      ranges.forEachIndexed { i, range ->
        val index = parameterIndex(i, ranges.size)
        parameters += "${SELECTION}${index}" to range
      }
    }

    return Urls.newFromEncoded("${JBProtocolCommand.SCHEME}://${tool}/${NAVIGATE_COMMAND}/${REFERENCE_TARGET}")
      .addParameters(parameters)
      .toExternalForm()
  }

  private fun isFile(element: PsiElement): Boolean = element is PsiFileSystemItem && FqnUtil.getQualifiedNameFromProviders(element) == null

  private fun parameterIndex(index: Int, size: Int): String = if (size == 1) "" else "${index + 1}"

  private fun getSelectionRange(editor: Editor, caret: Caret): String? {
    if (!caret.hasSelection()) {
      return null
    }

    val start = editor.offsetToLogicalPosition(caret.selectionStart)
    val end = editor.offsetToLogicalPosition(caret.selectionEnd)
    return "${start.line + 1}:${start.column + 1}-${end.line + 1}:${end.column + 1}"
  }
}
