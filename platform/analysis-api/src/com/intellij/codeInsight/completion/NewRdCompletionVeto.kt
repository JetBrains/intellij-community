// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus

/**
 * Allows forbidding new frontend-based completion support in RemoteDev
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
  fun veto(editor: Editor): Boolean
}
