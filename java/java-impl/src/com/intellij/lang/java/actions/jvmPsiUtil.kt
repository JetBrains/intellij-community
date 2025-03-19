// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.*
import com.intellij.codeInsight.ExpectedTypesProvider.createInfo
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.java.request.ExpectedJavaType
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.lang.jvm.actions.ExpectedTypeWithNullability
import com.intellij.lang.jvm.actions.ExpectedTypes
import com.intellij.lang.jvm.types.JvmSubstitutor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.PsiModifier.ModifierConstant
import com.intellij.psi.impl.source.PsiClassImpl
import com.intellij.psi.impl.source.jsp.jspJava.JspClass
import com.intellij.psi.util.PsiTypesUtil

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
  if (this is PsiClassImpl || this is JspClass || this is PsiImplicitClass) {
    // `is JspClass` check should be removed when JSP will define its own action factory,
    // since Java should know nothing about JSP.
    if ((this as PsiClass).language == JavaLanguage.INSTANCE) {
      return this
    }
  }
  return null
}

internal val visibilityModifiers: Set<JvmModifier> = setOf(
  JvmModifier.PUBLIC,
  JvmModifier.PROTECTED,
  JvmModifier.PACKAGE_LOCAL,
  JvmModifier.PRIVATE
)

internal fun extractExpectedTypes(project: Project, expectedTypes: ExpectedTypes, context:PsiElement): List<ExpectedTypeInfo> {
  return expectedTypes.map {
    toExpectedTypeInfo(project, it, context)
  }
}

private fun toExpectedTypeInfo(project: Project, expectedType: ExpectedType, context: PsiElement): ExpectedTypeInfo {
  if (expectedType is ExpectedJavaType) return expectedType.info
  val helper = JvmPsiConversionHelper.getInstance(project)
  var psiType = PsiTypesUtil.removeExternalAnnotations(helper.convertType(expectedType.theType))
  if (expectedType is ExpectedTypeWithNullability) {
    psiType =
    when (expectedType.nullability) {
      Nullability.NOT_NULL -> annotate(psiType, NullableNotNullManager.getInstance(project).defaultNotNull, project, context)
      Nullability.NULLABLE -> annotate(psiType, NullableNotNullManager.getInstance(project).defaultNullable, project, context)
      Nullability.UNKNOWN -> psiType
    }
  }
  return createInfo(psiType, expectedType.theKind.infoKind(), psiType, TailTypes.noneType())
}

private fun annotate(psiType: PsiType,
                     annoFqn: String,
                     project: Project,
                     context: PsiElement): PsiType {
  return if (AnnotationUtil.isAnnotatingApplicable(context, annoFqn) && psiType !is PsiPrimitiveType)
    psiType.annotate(TypeAnnotationProvider.Static.create(arrayOf(JavaPsiFacade.getElementFactory(project).createAnnotationFromText("@"+annoFqn, context))))
  else psiType
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

internal fun PsiType.toExpectedType(): ExpectedTypeInfo = createInfo(this, ExpectedTypeInfo.TYPE_STRICTLY, this, TailTypes.noneType())

internal fun List<ExpectedTypeInfo>.orObject(context: PsiElement): List<ExpectedTypeInfo> {
  if (isEmpty() || get(0).type == PsiTypes.voidType()) {
    return listOf(PsiType.getJavaLangObject(context.manager, context.resolveScope).toExpectedType())
  }
  return this
}
