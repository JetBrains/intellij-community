// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.fix

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.shortenReference
import java.util.function.Consumer

class ReplaceReferenceQuickFix(propertyName: String) : ModCommandQuickFix() {
  @SafeFieldForPreview
  private val qualifiedReference = PROPERTIES_TO_OPTIMIZE[propertyName] ?: error("Unknown property")

  override fun getFamilyName(): String = CommonQuickFixBundle.message("fix.replace.with.x", qualifiedReference)

  override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
    val uCall = descriptor.psiElement.getUastParentOfType<UCallExpression>()
    val uFactory = uCall?.getUastElementFactory(project)
    val oldUParent = uCall?.getQualifiedParentOrThis()
    val newUElement = uFactory?.createQualifiedReference(qualifiedReference.name, null)?.let {
      qualifiedReference.callExpressions.fold(it as? UExpression) { receiver, callExpressionArgs ->
        uFactory.createCallExpression(receiver?.getQualifiedParentOrThis(),
                                      callExpressionArgs.name,
                                      emptyList(),
                                      PsiType.getTypeByName(callExpressionArgs.returnType, project, GlobalSearchScope.allScope(project)),
                                      UastCallKind.METHOD_CALL)
      }
    } ?: return ModCommand.nop()

    val oldPsi = oldUParent?.sourcePsi ?: return ModCommand.nop()
    val newPsi = newUElement.getQualifiedParentOrThis().sourcePsi ?: return ModCommand.nop()
    return ModCommand.psiUpdate(oldPsi, Consumer { it.replace(newPsi).toUElementOfType<UReferenceExpression>()?.shortenReference() })
  }

  data class QualifiedReference(val name: String, val callExpressions: List<CallExpression>) {
    override fun toString(): String = if (callExpressions.isEmpty()) {
      name
    }
    else {
      "$name.${callExpressions.joinToString(separator = "().", postfix = "()") { it.name }}"
    }
  }

  data class CallExpression(val name: String, val returnType: String)

  companion object {
    val PROPERTIES_TO_OPTIMIZE = mapOf(
      "file.separator" to QualifiedReference("java.nio.file.FileSystems",
                                             listOf(CallExpression("getDefault", "java.nio.file.FileSystem"),
                                                    CallExpression("getSeparator", "java.lang.String"))),
      "path.separator" to QualifiedReference("java.io.File.pathSeparator", emptyList()),
      "line.separator" to QualifiedReference("java.lang.System",
                                             listOf(CallExpression("lineSeparator", "java.lang.String"))),
      "file.encoding" to QualifiedReference("java.nio.charset.Charset",
                                            listOf(CallExpression("defaultCharset", "java.nio.charset.Charset"),
                                                   CallExpression("displayName", "java.lang.String"))))
  }
}