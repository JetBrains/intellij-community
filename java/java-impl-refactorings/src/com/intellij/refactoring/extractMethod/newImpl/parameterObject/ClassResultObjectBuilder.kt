// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl.parameterObject

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.siyeh.ig.psiutils.TypeUtils

class ClassResultObjectBuilder(private val pojoClass: PsiClass): ResultObjectBuilder {
  companion object {
    fun create(variables: List<PsiVariable>): ClassResultObjectBuilder {
      return ClassResultObjectBuilder(createPojoClass(variables))
    }

    private fun createPojoClass(variables: List<PsiVariable>): PsiClass {
      val project = variables.first().project
      val factory = PsiElementFactory.getInstance(project)
      val pojoClass = factory.createClass("Result")
      variables.forEach { variable ->
        val field = factory.createField(variable.name!!, variable.type)
        field.modifierList?.setModifierProperty(PsiModifier.PUBLIC, true)
        field.modifierList?.setModifierProperty(PsiModifier.FINAL, true)
        pojoClass.add(field)
      }
      val constructor = factory.createConstructor()
      val body = factory.createCodeBlock()
      variables.forEach { variable ->
        val parameter = factory.createParameter(variable.name!!, variable.type)
        val assignment = factory.createStatementFromText("this.${variable.name} = ${variable.name};", constructor)
        constructor.parameterList.add(parameter)
        body.add(assignment)
      }
      constructor.body?.replace(body)
      pojoClass.add(constructor)
      pojoClass.modifierList?.setModifierProperty(PsiModifier.STATIC, true)
      return pojoClass
    }
  }

  override fun createClass(): PsiClass = pojoClass

  override fun createDeclaration(): PsiDeclarationStatement {
    val fields = pojoClass.allFields
    val parameters = fields.joinToString(separator = ",") { field -> field.name }
    val initializer = "new ${pojoClass.name}($parameters)"
    val factory = PsiElementFactory.getInstance(pojoClass.project)
    val expression = factory.createExpressionFromText(initializer, pojoClass)
    return factory.createVariableDeclarationStatement("result", TypeUtils.getType(pojoClass), expression)
  }

  override fun createReferenceReplacement(reference: PsiReferenceExpression): PsiExpression {
    return PsiElementFactory.getInstance(reference.project).createExpressionFromText("result.${reference.text}", reference.context)
  }

  override fun findVariableReferenceInReplacement(replacement: PsiExpression): PsiReferenceExpression? {
    val place = replacement.textRange.startOffset
    return PsiTreeUtil.findElementOfClassAtOffset(replacement.containingFile, place, PsiReferenceExpression::class.java, false)
  }
}