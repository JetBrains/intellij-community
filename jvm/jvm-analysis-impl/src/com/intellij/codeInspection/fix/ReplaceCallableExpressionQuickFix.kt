// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.fix

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.shortenReference

class ReplaceCallableExpressionQuickFix(@SafeFieldForPreview private val callableExpression: CallableExpression) : PsiUpdateModCommandQuickFix() {
  override fun getFamilyName(): String = CommonQuickFixBundle.message("fix.replace.with.x", callableExpression)

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val uCall = element.getUastParentOfType<UCallExpression>()
    val uFactory = uCall?.getUastElementFactory(project)
    val oldUParent = uCall?.getQualifiedParentOrThis()
    val uQualifiedReference = callableExpression.qualifiedReference?.let {
      uFactory?.createQualifiedReference(it.name, null)
    } ?: uCall?.receiver

    val newUElement = callableExpression.methods.fold(uQualifiedReference) { receiver, method ->
      uFactory?.createCallExpression(receiver?.getQualifiedParentOrThis(),
                                     method.name,
                                     method.parameters,
                                     extractReturnType(project, method),
                                     UastCallKind.METHOD_CALL)
    }

    val oldPsi = oldUParent?.sourcePsi ?: return
    val newPsi = newUElement?.getQualifiedParentOrThis()?.sourcePsi ?: return
    oldPsi.replace(newPsi).toUElementOfType<UReferenceExpression>()?.shortenReference()
  }

  private fun extractReturnType(project: Project, method: Method): PsiType? = method.returnType?.let {
    PsiType.getTypeByName(method.returnType, project, GlobalSearchScope.allScope(project))
  }
}

data class CallableExpression(val qualifiedReference: QualifiedReference?, val methods: List<Method>) {
  override fun toString(): String = if (qualifiedReference == null) {
    methods.joinToString(separator = "().", postfix = "()") { it.name }
  }
  else if (methods.isEmpty()) {
    qualifiedReference.name
  }
  else {
    "${qualifiedReference.name}.${methods.joinToString(separator = "().", postfix = "()") { it.name }}"
  }
}

@JvmInline
value class QualifiedReference(val name: String)

data class Method(val name: String, val returnType: String? = null, val parameters: List<UExpression> = emptyList())