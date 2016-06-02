/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.javaDoc

import com.intellij.codeInspection.ExternalAnnotatorInspectionVisitor
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.codeInspection.ex.PairedUnfairLocalInspectionTool
import com.intellij.psi.PsiElement

class JavadocHtmlLintInspection : LocalInspectionTool(), PairedUnfairLocalInspectionTool {
  companion object {
    val SHORT_NAME = "JavadocHtmlLint"
  }

  private val annotator = lazy { JavadocHtmlLintAnnotator(true) }

  override fun buildVisitor(holder: ProblemsHolder, onTheFly: Boolean) = ExternalAnnotatorInspectionVisitor(holder, annotator.value, onTheFly)

  override fun getBatchSuppressActions(element: PsiElement?) = SuppressQuickFix.EMPTY_ARRAY

  override fun getInspectionForBatchShortName() = SHORT_NAME
}