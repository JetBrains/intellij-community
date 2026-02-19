// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.jsonc

import com.intellij.json.codeinsight.JsonStandardComplianceProvider
import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiUtilCore

class JsoncComplianceProvider : JsonStandardComplianceProvider() {
  private val JSONC_DEFAULT_EXTENSION = "jsonc"

  override fun isCommentAllowed(comment: PsiComment): Boolean {
    val virtualFile = PsiUtilCore.getVirtualFile(comment)

    if (virtualFile?.extension == JSONC_DEFAULT_EXTENSION) {
      return true
    }

    return super.isCommentAllowed(comment)
  }
}