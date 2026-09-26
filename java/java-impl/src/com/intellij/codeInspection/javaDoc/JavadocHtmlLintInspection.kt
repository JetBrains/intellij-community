// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.javaDoc

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection
import com.intellij.psi.PsiElement

public class JavadocHtmlLintInspection : LocalInspectionTool(), ExternalAnnotatorBatchInspection {
  public companion object {
    public const val SHORT_NAME: String = "JavadocHtmlLint"
  }

  override fun getBatchSuppressActions(element: PsiElement?): Array<out SuppressQuickFix> = SuppressQuickFix.EMPTY_ARRAY
}