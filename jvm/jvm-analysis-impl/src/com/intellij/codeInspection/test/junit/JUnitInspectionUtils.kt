// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.siyeh.ig.junit.JUnitCommonClassNames.*

internal fun isJUnit3InScope(file: PsiFile): Boolean =
  JavaPsiFacade.getInstance(file.project).findClass(JUNIT_FRAMEWORK_TEST_CASE, file.resolveScope) != null

internal fun isJUnit4InScope(file: PsiFile): Boolean =
  JavaPsiFacade.getInstance(file.project).findClass(ORG_JUNIT_TEST, file.resolveScope) != null

internal fun isJUnit5InScope(file: PsiFile): Boolean =
  JavaPsiFacade.getInstance(file.project).findClass(ORG_JUNIT_JUPITER_API_TEST, file.resolveScope) != null