// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.actions.CopyReferenceUtil.*
import com.intellij.navigation.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.PlatformUtils.*
import com.intellij.util.io.encodeUrlQueryParameter
import java.util.stream.Collectors
import java.util.stream.IntStream

internal object CopyTBXReferenceAction {
  private val LOG = Logger.getInstance(CopyTBXReferenceAction::class.java)

  @NlsSafe
  private val IDE_TAGS = mapOf(IDEA_PREFIX to "idea",
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
                               DBE_PREFIX to "dbe")

  fun createJetBrainsLink(project: Project, elements: List<PsiElement>, editor: Editor?): String? {
    val entries = IntArray(elements.size) { i -> i }
      .associateBy({ it }, { elementToFqn(elements[it], editor) })
      .filter { it.value != null }
      .mapValues { FileUtil.getLocationRelativeToUserHome(it.value, false) }
      .entries

    val refsParameters = if (entries.isEmpty()) null
    else entries.joinToString("") {
      createRefs(isFile(elements[it.key]), if (elements.size > 1) it.value.encodeUrlQueryParameter() else it.value,
                 parameterIndex(it.key, elements.size))
    }

    val copy = createLink(editor, project, refsParameters)
    if (copy != null) return copy

    if (editor == null) return null
    val file = PsiDocumentManager.getInstance(project).getCachedPsiFile(editor.document) ?: return null

    val logicalPosition = editor.caretModel.logicalPosition
    val path = "${getFileFqn(file)}:${logicalPosition.line + 1}:${logicalPosition.column + 1}"

    return createLink(editor, project, createRefs(true, path, ""))
  }

  private fun isFile(element: PsiElement) = element is PsiFileSystemItem && getQualifiedNameFromProviders(element) == null

  private fun parameterIndex(index: Int, size: Int) = if (size == 1) "" else "${index + 1}"

  private fun createRefs(isFile: Boolean, reference: String?, index: String): String {
    val navigationKey = if (isFile) NavigatorWithinProject.NavigationKeyPrefix.PATH else NavigatorWithinProject.NavigationKeyPrefix.FQN
    return "&${navigationKey}${index}=$reference"
  }

  private fun createLink(editor: Editor?, project: Project, refsParameters: String?): String? {
    if (refsParameters == null) return null

    val tool = IDE_TAGS[getPlatformPrefix()]
    if (tool == null) {
      LOG.warn("Cannot find TBX tool for IDE: ${getPlatformPrefix()}")
      return null
    }

    val selectionParameters = getSelectionParameters(editor) ?: ""
    val projectParameter = "${PROJECT_NAME_KEY}=${project.name}"

    return "${JBProtocolCommand.SCHEME}://${tool}/${NAVIGATE_COMMAND}/${REFERENCE_TARGET}?${projectParameter}${refsParameters}${selectionParameters}"
  }

  private fun getSelectionParameters(editor: Editor?): String? {
    if (editor == null) {
      return null
    }

    ApplicationManager.getApplication().assertReadAccessAllowed()
    if (editor.caretModel.supportsMultipleCarets()) {
      val carets = editor.caretModel.allCarets
      return IntStream.range(0, carets.size).mapToObj { i -> getSelectionParameters(editor, carets[i], parameterIndex(i, carets.size)) }
        .filter { it != null }.collect(Collectors.joining())
    }
    else {
      return getSelectionParameters(editor, editor.caretModel.currentCaret, "")
    }
  }

  private fun getSelectionParameters(editor: Editor, caret: Caret, index: String): String? =
    getSelectionRange(editor, caret)?.let {
      "&$SELECTION$index=$it"
    }

  private fun getSelectionRange(editor: Editor, caret: Caret): String? {
    if (!caret.hasSelection()) {
      return null
    }

    val selectionStart = editor.offsetToLogicalPosition(caret.selectionStart)
    val selectionEnd = editor.offsetToLogicalPosition(caret.selectionEnd)

    return String.format("%d:%d-%d:%d",
                         selectionStart.line + 1,
                         selectionStart.column + 1,
                         selectionEnd.line + 1,
                         selectionEnd.column + 1)
  }
}
