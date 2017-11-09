// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.ExpectedTypeInfo
import com.intellij.codeInsight.ExpectedTypesProvider
import com.intellij.codeInsight.TailType
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.java.request.ExpectedJavaType
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.lang.jvm.actions.ExpectedTypes
import com.intellij.lang.jvm.types.JvmSubstitutor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.PsiModifier.ModifierConstant
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.impl.compiled.ClsClassImpl

@ModifierConstant
internal fun JvmModifier.toPsiModifier(): String = when (this) {
  JvmModifier.PUBLIC -> PsiModifier.PUBLIC
  JvmModifier.PROTECTED -> PsiModifier.PROTECTED
  JvmModifier.PRIVATE -> PsiModifier.PRIVATE
  JvmModifier.PACKAGE_LOCAL -> PsiModifier.PACKAGE_LOCAL
  JvmModifier.STATIC -> PsiModifier.STATIC
  JvmModifier.ABSTRACT -> PsiModifier.ABSTRACT
  JvmModifier.FINAL -> PsiModifier.FINAL
  JvmModifier.NATIVE -> PsiModifier.NATIVE
  JvmModifier.SYNCHRONIZED -> PsiModifier.NATIVE
  JvmModifier.STRICTFP -> PsiModifier.STRICTFP
  JvmModifier.TRANSIENT -> PsiModifier.TRANSIENT
  JvmModifier.VOLATILE -> PsiModifier.VOLATILE
  JvmModifier.TRANSITIVE -> PsiModifier.TRANSITIVE
}

/**
 * Compiled classes, type parameters are not considered classes.
 *
 * @return Java PsiClass or `null` if the receiver is not a Java PsiClass
 */
internal fun JvmClass.toJavaClassOrNull(): PsiClass? {
  if (this !is PsiClass) return null
  if (this is PsiTypeParameter) return null
  if (this is ClsClassImpl) return null
  if (this.language != JavaLanguage.INSTANCE) return null
  return this
}

internal val visibilityModifiers = setOf(
  JvmModifier.PUBLIC,
  JvmModifier.PROTECTED,
  JvmModifier.PACKAGE_LOCAL,
  JvmModifier.PRIVATE
)

internal fun extractExpectedTypes(project: Project, expectedTypes: ExpectedTypes): List<ExpectedTypeInfo> {
  return expectedTypes.mapNotNull {
    toExpectedTypeInfo(project, it)
  }
}

private fun toExpectedTypeInfo(project: Project, expectedType: ExpectedType): ExpectedTypeInfo? {
  if (expectedType is ExpectedJavaType) return expectedType.info
  val helper = JvmPsiConversionHelper.getInstance(project)
  val psiType = helper.convertType(expectedType.theType) ?: return null
  return ExpectedTypesProvider.createInfo(psiType, expectedType.theKind.infoKind(), psiType, TailType.NONE)
}

@ExpectedTypeInfo.Type
private fun ExpectedType.Kind.infoKind(): Int {
  return when (this) {
    ExpectedType.Kind.EXACT -> ExpectedTypeInfo.TYPE_STRICTLY
    ExpectedType.Kind.SUPERTYPE -> ExpectedTypeInfo.TYPE_OR_SUPERTYPE
    ExpectedType.Kind.SUBTYPE -> ExpectedTypeInfo.TYPE_OR_SUBTYPE
  }
}

internal fun JvmSubstitutor.toPsiSubstitutor(project: Project): PsiSubstitutor {
  return JvmPsiConversionHelper.getInstance(project).convertSubstitutor(this)
}

internal inline fun extractNames(suggestedNames: SuggestedNameInfo?, defaultName: () -> String): Array<out String> {
  val names = (suggestedNames ?: SuggestedNameInfo.NULL_INFO).names
  return if (names.isEmpty()) arrayOf(defaultName()) else names
}
