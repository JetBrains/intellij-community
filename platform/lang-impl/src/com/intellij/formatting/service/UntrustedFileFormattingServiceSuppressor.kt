// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service

import com.intellij.ide.trustedProjects.TrustedFiles
import com.intellij.psi.PsiFile

/**
 * Keeps external formatters away from files opened in the safe mode (see [TrustedFiles]):
 * tools like Prettier discover their configs and plugins next to the formatted file and execute them,
 * so reformatting an untrusted file must fall back to the built-in formatter.
 */
internal class UntrustedFileFormattingServiceSuppressor : FormattingServiceSuppressor {
  override fun isSuppressed(file: PsiFile, service: FormattingService): Boolean {
    if (service !is AsyncDocumentFormattingService && service !is ExternalFormatProcessorAdapter) {
      return false
    }
    val virtualFile = file.virtualFile ?: return false
    return !TrustedFiles.isTrusted(virtualFile, file.project)
  }
}
