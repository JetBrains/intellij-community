// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework

import com.intellij.psi.PsiElement

class JavaTestDiffProvider : JvmTestDiffProvider() {
  override fun getStringLiteral(expected: PsiElement) = expected
}