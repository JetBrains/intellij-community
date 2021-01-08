// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.codeInsight.generation.GenerateMembersUtil
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.impl.source.codeStyle.JavaCodeStyleManagerImpl
import com.intellij.psi.search.LocalSearchScope
import com.intellij.refactoring.extractMethod.ExtractMethodDialog
import com.intellij.refactoring.extractMethod.InputVariables
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.Nls
import java.util.*

object MapFromDialog {
  fun mapFromDialog(extractOptions: ExtractOptions, @NlsContexts.DialogTitle title: String, helpId: String): ExtractOptions? {
    val dialog = createDialog(extractOptions, title, helpId)
    val isOk = dialog.showAndGet()
    if (isOk){
      return ExtractMethodPipeline.remap(extractOptions, dialog.chosenParameters, dialog.chosenMethodName,
                                             dialog.isMakeStatic, dialog.visibility, dialog.isChainedConstructor, dialog.returnType)
    } else {
      return null
    }
  }

  private fun createDialog(extractOptions: ExtractOptions, @NlsContexts.DialogTitle refactoringName: String, helpId: String): ExtractMethodDialog {
    val project = extractOptions.project
    val returnType = extractOptions.dataOutput.type
    val thrownExceptions = extractOptions.thrownExceptions.toTypedArray()
    val isStatic = extractOptions.isStatic
    val typeParameters = extractOptions.typeParameters
    val targetClass = extractOptions.anchor.containingClass
    val elements = extractOptions.elements.toTypedArray()
    val nullability = extractOptions.dataOutput.nullability.takeIf { ExtractMethodHelper.isNullabilityAvailable(extractOptions) }
    val analyzer = CodeFragmentAnalyzer(extractOptions.elements)
    val staticOptions = ExtractMethodPipeline.withForcedStatic(analyzer, extractOptions)
    val canBeStatic = ExtractMethodPipeline.withForcedStatic(analyzer, extractOptions) != null
    val canBeChainedConstructor = ExtractMethodPipeline.canBeConstructor(analyzer)
    val factory = PsiElementFactory.getInstance(project)
    val variables = extractOptions.inputParameters
      .map { factory.createVariableDeclarationStatement(it.name, it.type, null, it.references.first().context) }
      .map { declaration -> declaration.declaredElements[0] as PsiVariable }
    val parameterNames = extractOptions.inputParameters.map { it.name }.toSet()
    val fields = staticOptions?.inputParameters.orEmpty()
      .filterNot { it.name in parameterNames }.map { factory.createField(it.name, it.type) }.toSet()
    val inputVariables = InputVariables(
      variables,
      extractOptions.project,
      LocalSearchScope(extractOptions.elements.toTypedArray()),
      false,
      fields
    )
    val typeParameterList = PsiElementFactory.getInstance(extractOptions.project).createTypeParameterList()
    typeParameters.forEach { typeParameterList.add(it) }

    val methodNames = ExtractMethodHelper.guessMethodName(extractOptions)

    return object: ExtractMethodDialog(project, targetClass, inputVariables, returnType, typeParameterList,
                                       thrownExceptions, isStatic, canBeStatic, canBeChainedConstructor,
                                       refactoringName, helpId, nullability, elements, {0}) {
      override fun areTypesDirected() = true

      override fun suggestMethodNames(): Array<String> {
        return methodNames.toTypedArray()
      }

      override fun isVoidReturn(): Boolean = false

      override fun findOccurrences(): Array<PsiExpression> {
        return when (val dataOutput = extractOptions.dataOutput) {
          is DataOutput.VariableOutput -> CodeInsightUtil.findReferenceExpressions(extractOptions.anchor, dataOutput.variable)
          is DataOutput.ExpressionOutput -> dataOutput.returnExpressions.toTypedArray()
          else -> emptyArray()
        }
      }

      override fun isOutputVariable(variable: PsiVariable): Boolean {
        return (extractOptions.dataOutput as? DataOutput.VariableOutput)?.variable == variable
      }

      override fun checkMethodConflicts(conflicts: MultiMap<PsiElement, String>) {
        super.checkMethodConflicts(conflicts)
        val parameters = chosenParameters
        val vars: MutableMap<String, PsiLocalVariable> = HashMap()
        for (element in elements) {
          element.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitLocalVariable(variable: PsiLocalVariable) {
              super.visitLocalVariable(variable)
              vars[variable.name] = variable
            }

            override fun visitClass(aClass: PsiClass) {}
          })
        }
        for (parameter in parameters) {
          val paramName = parameter.name
          val variable = vars[paramName]
          if (variable != null) {
            conflicts.putValue(variable, JavaRefactoringBundle.message("extract.method.conflict.variable", paramName))
          }
        }
      }

      override fun hasPreviewButton() = false
    }
  }

}