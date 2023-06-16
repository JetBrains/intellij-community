// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.jsonc

import com.intellij.json.codeinsight.JsonStandardComplianceProvider
import com.intellij.psi.PsiComment

class JsoncComplianceProvider : JsonStandardComplianceProvider() {
  override fun isCommentAllowed(comment: PsiComment): Boolean {
    if (comment.containingFile.virtualFile.nameSequence.endsWith(JsoncFileType.DEFAULT_EXTENSION)) {
      return true
    }

    return super.isCommentAllowed(comment)
  }
}