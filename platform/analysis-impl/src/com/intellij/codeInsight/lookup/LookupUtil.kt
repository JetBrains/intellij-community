// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
object LookupUtil {
  private val LOG = logger<LookupUtil>()

  @JvmStatic
  fun insertLookupInDocumentWindowIfNeeded(project: Project,
                                           editor: Editor, caretOffset: Int,
                                           prefix: Int,
                                           lookupString: String): Int {
    val document = getInjectedDocument(project, editor, caretOffset)
    if (document == null) return insertLookupInDocument(caretOffset, editor.document, prefix, lookupString)
    val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
    val offset = document.hostToInjected(caretOffset)
    val lookupStart = min(offset, max(offset - prefix, 0))
    var diff = -1
    if (file != null) {
      val ranges = InjectedLanguageManager.getInstance(project)
        .intersectWithAllEditableFragments(file, TextRange.create(lookupStart, offset))
      if (ranges.isNotEmpty()) {
        diff = ranges[0].startOffset - lookupStart
        if (ranges.size == 1 && diff == 0) diff = -1
      }
    }
    return if (diff == -1) insertLookupInDocument(caretOffset, editor.document, prefix, lookupString)
    else document.injectedToHost(
      insertLookupInDocument(offset, document, prefix - diff, if (diff == 0) lookupString else lookupString.substring(diff))
    )
  }

  @JvmStatic
  fun getCaseCorrectedLookupString(item: LookupElement, prefixMatcher: PrefixMatcher, prefix: String): String {
    val lookupString = item.lookupString
    if (item.isCaseSensitive) {
      return lookupString
    }
    val length = prefix.length
    if (length == 0 || !prefixMatcher.prefixMatches(prefix)) return lookupString
    var isAllLower = true
    var isAllUpper = true
    var sameCase = true
    var i = 0
    while (i < length && (isAllLower || isAllUpper || sameCase)) {
      val c = prefix[i]
      val isLower = c.isLowerCase()
      val isUpper = c.isUpperCase()
      // do not take this kind of symbols into account ('_', '@', etc.)
      if (!isLower && !isUpper) {
        i++
        continue
      }
      isAllLower = isAllLower && isLower
      isAllUpper = isAllUpper && isUpper
      sameCase = sameCase && i < lookupString.length && isLower == lookupString[i].isLowerCase()
      i++
    }
    if (sameCase) return lookupString
    if (isAllLower) return StringUtil.toLowerCase(lookupString)
    return if (isAllUpper) StringUtil.toUpperCase(lookupString) else lookupString
  }

  private fun getInjectedDocument(project: Project,
                                  editor: Editor,
                                  offset: Int): DocumentWindow? {
    val hostFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    if (hostFile != null) { // inspired by com.intellij.codeInsight.editorActions.TypedHandler.injectedEditorIfCharTypedIsSignificant()
      val injected = InjectedLanguageManager.getInstance(project)
        .getCachedInjectedDocumentsInRange(hostFile, TextRange.create(offset, offset))
      for (documentWindow in injected) {
        if (documentWindow.isValid && documentWindow.containsRange(offset, offset)) {
          return documentWindow
        }
      }
    }
    return null
  }

  private fun insertLookupInDocument(caretOffset: Int, document: Document, prefix: Int, lookupString: String): Int {
    val lookupStart = min(caretOffset, max(caretOffset - prefix, 0))
    val len = document.textLength
    LOG.assertTrue(lookupStart in 0..len, "ls: $lookupStart caret: $caretOffset prefix:$prefix doc: $len")
    LOG.assertTrue(caretOffset in 0..len, "co: $caretOffset doc: $len")
    document.replaceString(lookupStart, caretOffset, lookupString)
    return lookupStart + lookupString.length
  }

  @JvmStatic
  fun performGuardedChange(editor: Editor?, action: Runnable) {
    val lookup = editor?.let(LookupManager::getActiveLookup)
    if (lookup == null) {
      action.run()
    }
    else {
      lookup.performGuardedChange(action)
    }
  }
}