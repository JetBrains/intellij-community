// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.suggested

import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo
import com.intellij.refactoring.suggested.SuggestedChangeSignatureData
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport
import com.intellij.refactoring.util.CanonicalTypes

class JavaSuggestedRefactoringExecution(refactoringSupport: SuggestedRefactoringSupport) :
  SuggestedRefactoringExecution(refactoringSupport) {
  override fun prepareChangeSignature(data: SuggestedChangeSignatureData): Any? {
    val method = data.declaration as PsiMethod
    return extractTypes(method, method.containingFile)
  }

  private data class ExtractedTypes(
    val parameterTypes: List<PsiType>,
    val returnType: PsiType?,
    val exceptionTypes: List<PsiClassType>
  )

  private fun extractTypes(
    method: PsiMethod,
    psiFile: PsiFile
  ): ExtractedTypes {
    val parameterTypes = method.parameterList.parameters.map {
      it.type.copyWithAnnotations(it, psiFile)
    }
    val returnType = method.returnType?.copyWithAnnotations(method, psiFile)
    val exceptionTypes = method.throwsList.referencedTypes
      .map { CanonicalTypes.createTypeWrapper(it).getType(psiFile) as PsiClassType }

    return ExtractedTypes(parameterTypes, returnType, exceptionTypes)
  }

  private fun PsiType.copyWithAnnotations(owner: PsiModifierListOwner, file: PsiFile): PsiType {
    val factory = PsiElementFactory.getInstance(file.project)
    val annotations = JavaSuggestedRefactoringSupport.extractAnnotationsToCopy(this, owner, file)
    //TODO: it's a hack to workaround ChangeSignatureProcessor comparing types by presentable text without annotations
    return factory.createTypeFromText(this.annotate { annotations.toTypedArray() }.getCanonicalText(true), null)
  }

  override fun performChangeSignature(data: SuggestedChangeSignatureData, newParameterValues: List<NewParameterValue>, preparedData: Any?) {
    val declaration = data.declaration as PsiMethod
    val file = declaration.containingFile
    val project = file.project
    val (newParameterTypes, newReturnType, newExceptionTypes) = preparedData as ExtractedTypes

    val (oldParameterTypes, oldReturnType, oldExceptionTypes) = extractTypes(declaration, file)
    val oldPsiParameters = declaration.parameterList.parameters

    var newParameterValueIndex = 0
    val newParameters = data.newSignature.parameters.mapIndexed { index, parameter ->
      val initialIndex = data.oldSignature.parameterById(parameter.id)
        ?.let { data.oldSignature.parameterIndex(it) }

      // if there no changes in type and annotations, we use original type without annotations
      val type = if (initialIndex == null || !typesEqualWithAnnotations(newParameterTypes[index], oldParameterTypes[initialIndex])) {
        newParameterTypes[index]
      }
      else {
        oldPsiParameters[initialIndex].type
      }

      val info = ParameterInfoImpl(initialIndex ?: -1, parameter.name, type)

      if (initialIndex == null) {
        when (val default = newParameterValues[newParameterValueIndex++]) {
          is NewParameterValue.Expression -> {
            val fragment = default.expression
            executeCommand {
              runUndoTransparentWriteAction {
               info.defaultValue = JavaCodeStyleManager.getInstance(fragment.project).qualifyClassReferences(fragment).text
              }
            }
          }
          is NewParameterValue.AnyVariable -> info.isUseAnySingleVariable = true
          is NewParameterValue.None -> {
          }
        }
      }

      info
    }

    // if there no changes in type and annotations, we use original type without annotations
    val returnType = if (!typesEqualWithAnnotations(newReturnType, oldReturnType))
      newReturnType
    else
      declaration.returnType

    val exceptionInfos = prepareExceptionInfos(newExceptionTypes, oldExceptionTypes)

    val processor = ChangeSignatureProcessor(
      project,
      declaration,
      false,
      data.newSignature.visibility?.takeIf { it != data.oldSignature.visibility },
      data.newSignature.name,
      returnType,
      newParameters.toTypedArray(),
      exceptionInfos.toTypedArray()
    )
    processor.run()
  }

  private fun typesEqualWithAnnotations(type1: PsiType?, type2: PsiType?) = type1?.getCanonicalText(true) == type2?.getCanonicalText(true)

  private fun prepareExceptionInfos(
    newExceptionTypes: List<PsiClassType>,
    oldExceptionTypes: List<PsiClassType>
  ): List<ThrownExceptionInfo> {
    val oldTypes = oldExceptionTypes.toMutableList<PsiType?>()
    return newExceptionTypes.map { newExceptionType ->
      val oldIndex = oldTypes.indexOfFirst { it != null && it == newExceptionType }
      if (oldIndex >= 0) {
        oldTypes[oldIndex] = null
      }
      JavaThrownExceptionInfo(oldIndex, newExceptionType)
    }
  }
}
