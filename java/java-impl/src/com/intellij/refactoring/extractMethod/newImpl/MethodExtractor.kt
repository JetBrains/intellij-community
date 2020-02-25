// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.extractMethod.ExtractMethodDialog
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.addSiblingAfter
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.wrapWithCodeBlock
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput.*
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase

fun extractMethod(dependencies: ExtractOptions) {
  val factory = PsiElementFactory.getInstance(dependencies.project)
  val styleManager = CodeStyleManager.getInstance(dependencies.project)
  val flowOutput = dependencies.flowOutput
  val newFlowOutput = if (dependencies.dataOutput is ExpressionOutput && flowOutput is ConditionalFlow) {
    flowOutput.copy(statements = flowOutput.statements.filterNot { it is PsiReturnStatement })
  }
  else {
    flowOutput
  }
  val codeBlock = with(dependencies) {
    BodyBuilder(factory).build(
      elements = elements,
      flowOutput = newFlowOutput,
      dataOutput = dataOutput,
      inputParameters = inputParameters,
      missedDeclarations = requiredVariablesInside
    )
  }
  val signature = SignatureBuilder(dependencies.project)
    .build(
      isStatic = dependencies.isStatic,
      visibility = dependencies.visibility,
      typeParameters = dependencies.typeParameters,
      returnType = dependencies.dataOutput.type.takeIf { !dependencies.isConstructor },
      methodName = dependencies.methodName,
      inputParameters = dependencies.inputParameters,
      thrownExceptions = dependencies.thrownExceptions
    )
  val method = styleManager.reformat(signature) as PsiMethod
  method.body?.replace(codeBlock)

  if (needsNullabilityAnnotations(dependencies.project)) {
    updateMethodAnnotations(method)
  }

  ApplicationManager.getApplication().runWriteAction {

    dependencies.anchor.addSiblingAfter(method)

    val methodCall = dependencies.methodName + "(" + dependencies.inputParameters.joinToString { it.references.first().text } + ")"
    val expressionElement = (dependencies.elements.singleOrNull() as? PsiExpression)
    if (expressionElement != null) {
      val callExpression = PsiElementFactory.getInstance(expressionElement.project).createExpressionFromText(methodCall, null)
      IntroduceVariableBase.replace(expressionElement, callExpression, expressionElement.project)
    }
    else {
      val callElements = CallFactory(PsiElementFactory.getInstance(dependencies.project)).buildCall(
        methodCall = methodCall,
        flowOutput = dependencies.flowOutput,
        dataOutput = dependencies.dataOutput,
        exposedDeclarations = dependencies.exposedLocalVariables
      )

      val elementsToAdd = when {
        callElements.size > 1 && dependencies.elements.first().parent !is PsiCodeBlock -> wrapWithCodeBlock(callElements)
        else -> callElements
      }
      elementsToAdd.reversed().forEach { statement ->
        val addedStatement = dependencies.elements.last().addSiblingAfter(statement)
        styleManager.reformat(addedStatement)
      }
      dependencies.elements.first().parent.deleteChildRange(dependencies.elements.first(), dependencies.elements.last())
    }
  }
}

private fun needsNullabilityAnnotations(project: Project): Boolean {
  return  PropertiesComponent.getInstance(project).getBoolean(ExtractMethodDialog.EXTRACT_METHOD_GENERATE_ANNOTATIONS, true)
}