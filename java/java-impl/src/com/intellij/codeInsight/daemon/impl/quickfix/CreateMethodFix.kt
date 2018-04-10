// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.codeInspection.IntentionWrapper.wrapToQuickFixes
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.createMethodActions
import com.intellij.lang.jvm.actions.methodRequest
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.JvmCommon
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType

fun createVoidMethodFixes(psiClass: @JvmCommon PsiClass, methodName: String, modifier: JvmModifier): Array<LocalQuickFix> {
  if (!ModuleUtilCore.projectContainsFile(psiClass.project, psiClass.containingFile.virtualFile, false)) return LocalQuickFix.EMPTY_ARRAY
  val request = methodRequest(psiClass.project, methodName, modifier, PsiType.VOID)
  val actions = createMethodActions(psiClass, request)
  if (actions.isEmpty()) return LocalQuickFix.EMPTY_ARRAY
  return wrapToQuickFixes(
    actions,
    psiClass.containingFile
  ).toTypedArray()
}
