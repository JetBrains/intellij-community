// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.fix

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.getQualifiedParentOrThis
import org.jetbrains.uast.getUastParentOfType

class ReplaceMethodCallFix(val methodName: String) : LocalQuickFix {
  override fun getFamilyName(): String = CommonQuickFixBundle.message("fix.replace.with.x", "$methodName()")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val uCall = descriptor.psiElement.getUastParentOfType<UCallExpression>() ?: return
    val uFactory = uCall.getUastElementFactory(project) ?: return
    val newCall = uFactory.createCallExpression(
      uCall.receiver, methodName, uCall.valueArguments, null, uCall.kind, null
    ) ?: return
    val oldCall = uCall.getQualifiedParentOrThis()
    oldCall.replace(newCall)
  }
}