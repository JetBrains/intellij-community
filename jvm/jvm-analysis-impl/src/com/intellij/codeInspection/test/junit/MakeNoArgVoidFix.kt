// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.jvm.JvmMethod
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.castSafelyTo
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getUParentForIdentifier

class MakeNoArgVoidFix(
  private val methodName: @NlsSafe String,
  private val makeStatic: Boolean,
  private val newVisibility: JvmModifier? = null
) : LocalQuickFix {
  override fun getName(): String = JvmAnalysisBundle.message("jvm.fix.make.no.arg.void.descriptor", methodName)

  override fun getFamilyName(): String = JvmAnalysisBundle.message("jvm.fix.make.no.arg.void.name")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement
    val uMethod = getUParentForIdentifier(element)?.castSafelyTo<UMethod>() ?: return
    val jvmMethod = uMethod.javaPsi.castSafelyTo<JvmMethod>() ?: return
    val changeTypeActions = createChangeTypeActions(jvmMethod, typeRequest(JvmPrimitiveTypeKind.VOID.name, emptyList()))
    val removeParametersActions = createChangeParametersActions(jvmMethod, setMethodParametersRequest(emptyMap<String, JvmType>().entries))
    val visibilityActions = if (newVisibility != null) {
      createModifierActions(jvmMethod, modifierRequest(newVisibility, true))
    } else emptyList()
    val makeStaticActions = createModifierActions(jvmMethod, modifierRequest(JvmModifier.STATIC, makeStatic))
    (changeTypeActions + visibilityActions + removeParametersActions + makeStaticActions).forEach {
      it.invoke(project, null, element.containingFile)
    }
  }
}