// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.ide.trustedProjects.TrustedFiles
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.annotations.ApiStatus

/**
 * Gates refactoring actions based on the trust of the context file (see [TrustedFiles]).
 *
 * Refactoring is allowed in trusted files. For untrusted files, a refactoring is allowed only
 * when the resolved target belongs to the same file.
 */
@ApiStatus.Internal
object RefactoringSafeMode {
  /**
   * Returns `true` when a refactoring may start from the file in [dataContext].
   */
  @JvmStatic
  fun isRefactoringAllowed(dataContext: DataContext): Boolean {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return true

    val contextFile = dataContext.getData(CommonDataKeys.PSI_FILE)
                        ?.let { InjectedLanguageManager.getInstance(project).getTopLevelFile(it) }
                        ?.virtualFile
                      ?: dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
                      ?: return true

    return TrustedFiles.isTrusted(contextFile, project)
  }
}
