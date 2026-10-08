// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.openapi.editor.Editor
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

/**
 * Allows forbidding new frontend-based completion support in RemoteDev
 *
 * The platform calls [veto] under a read action, on any thread.
 * The implementation must be fast and must not block.
 *
 * ```
 * internal class MyLangNewRdVeto : NewRdCompletionVeto {
 *   override fun veto(editor: Editor): Boolean {
 *     val project = editor.project ?: return false
 *     val file = PsiUtilBase.getPsiFileInEditor(editor, project) ?: return false
 *     return file.language == MyLang.INSTANCE
 *   }
 * }
 * ```
 */
@ApiStatus.Internal
interface NewRdCompletionVeto {
  @RequiresReadLock
  fun veto(editor: Editor): Boolean
}
