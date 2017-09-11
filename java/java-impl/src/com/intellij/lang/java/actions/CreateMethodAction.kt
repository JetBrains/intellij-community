/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.java.actions

import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.positionCursor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.*
import com.intellij.codeInsight.daemon.impl.quickfix.GuessTypeParameters
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilder
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.lang.java.actions.Workaround.extractExpectedTypes
import com.intellij.lang.java.request.CreateMethodFromJavaUsageRequest
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.PsiUtil.setModifierProperty
import com.intellij.psi.util.createSmartPointer

/**
 * @param abstract whether this action creates a method with explicit abstract modifier
 */
internal class CreateMethodAction(
  targetClass: PsiClass,
  private val abstract: Boolean,
  private val myRequest: CreateMethodRequest
) : BaseIntentionAction() {

  override fun getFamilyName(): String = message("create.method.from.usage.family")

  override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = myTargetClass.element

  override fun startInWriteAction(): Boolean = true

  private val myTargetClass = targetClass.createSmartPointer()

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    val targetClass = myTargetClass.element ?: return false
    if (!myRequest.isValid) return false

    val name = myRequest.methodName
    if (!PsiNameHelper.getInstance(targetClass.project).isIdentifier(name)) return false

    val requestedModifiers = myRequest.modifiers
    val static = JvmModifier.STATIC in requestedModifiers

    if (abstract && static) return false

    if (abstract) {
      if (!targetClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false
      if (targetClass.isInterface) return false
    }
    else if (static) {
      // static method in interfaces are allowed starting with Java 8
      if (targetClass.isInterface && !PsiUtil.isLanguageLevel8OrHigher(targetClass)) return false
      // static methods in inner classes are disallowed JLS §8.1.3
      if (targetClass.containingClass != null && !targetClass.hasModifierProperty(PsiModifier.STATIC)) return false
    }

    val className = getNameForClass(targetClass, false)
    text = if (abstract) {
      message("create.abstract.method.from.usage.full.text", name, className)
    }
    else {
      message("create.method.from.usage.full.text", name, className)
    }
    return true
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val targetClass = myTargetClass.element ?: error("Target class was invalidated between isAvailable() and invoke()")
    assert(myRequest.isValid) { "Request was invalidates between isAvailable() and invoke()" }
    JavaMethodRenderer(project, abstract, targetClass, myRequest).doMagic()
  }
}

private class JavaMethodRenderer(
  val project: Project,
  val abstract: Boolean,
  val targetClass: PsiClass,
  val request: CreateMethodRequest
) {

  val factory = JavaPsiFacade.getElementFactory(project)!!
  val requestedModifiers = request.modifiers
  val javaUsage = request as? CreateMethodFromJavaUsageRequest
  val withoutBody = abstract || targetClass.isInterface && JvmModifier.STATIC !in requestedModifiers

  fun doMagic() {
    var method = renderMethod()
    method = insertMethod(method)
    method = forcePsiPostprocessAndRestoreElement(method) ?: return
    val builder = setupTemplate(method)
    method = forcePsiPostprocessAndRestoreElement(method) ?: return
    val template = builder.buildInlineTemplate()
    startTemplate(method, template)
  }

  private fun renderMethod(): PsiMethod {
    val method = factory.createMethod(request.methodName, PsiType.VOID)

    var modifiersToRender = requestedModifiers
    if (targetClass.isInterface) {
      modifiersToRender -= (visibilityModifiers + JvmModifier.ABSTRACT)
    }
    else if (abstract) {
      modifiersToRender += JvmModifier.ABSTRACT
    }

    for (modifier in modifiersToRender) {
      setModifierProperty(method, modifier.toPsi(), true)
    }

    for (annotation in request.annotations) {
      method.modifierList.addAnnotation(annotation.qualifiedName)
    }

    if (withoutBody) method.body?.delete()

    return method
  }

  private fun insertMethod(method: PsiMethod): PsiMethod {
    val anchor = javaUsage?.getAnchor(targetClass)
    val inserted = if (anchor == null) {
      targetClass.add(method)
    }
    else {
      targetClass.addAfter(method, anchor)
    }
    return inserted as PsiMethod
  }

  private fun setupTemplate(method: PsiMethod): TemplateBuilderImpl {
    val builder = TemplateBuilderImpl(method)
    val guesser = GuessTypeParameters(project, factory, builder, javaUsage?.targetSubstitutor)
    setupTypeElement(guesser, method.returnTypeElement, request.returnType)
    builder.setupParameters(guesser, method)
    builder.setEndVariableAfter(method.body ?: method)
    return builder
  }

  private fun setupTypeElement(guesser: GuessTypeParameters, typeElement: PsiTypeElement?, types: Any?) {
    typeElement ?: return
    val expectedTypes = extractExpectedTypes(types) ?: emptyArray()
    guesser.setupTypeElement(typeElement, expectedTypes, javaUsage?.context, targetClass)
  }

  private fun TemplateBuilder.setupParameters(guesser: GuessTypeParameters, method: PsiMethod) {
    val parameters = request.parameters
    if (parameters.isEmpty()) return
    val codeStyleManager = CodeStyleManager.getInstance(project)!!
    val parameterList = method.parameterList
    val isInterface = targetClass.isInterface
    //255 is the maximum number of method parameters
    for (i in 0 until minOf(parameters.size, 255)) {
      val parameterInfo = parameters[i]
      val names = extractNames(parameterInfo.first) { "p" + i }
      val dummyParameter = factory.createParameter(names.first(), PsiType.INT)
      if (isInterface) {
        setModifierProperty(dummyParameter, PsiModifier.FINAL, false)
      }
      val parameter = codeStyleManager.performActionWithFormatterDisabled(Computable {
        parameterList.add(dummyParameter)
      }) as PsiParameter
      setupTypeElement(guesser, parameter.typeElement, parameterInfo.second)
      setupParameterName(parameter, names)
    }
  }

  private fun startTemplate(method: PsiMethod, template: Template) {
    val targetFile = targetClass.containingFile
    val newEditor = positionCursor(project, targetFile, method) ?: return
    val templateListener = if (withoutBody) null else MyMethodBodyListener(project, newEditor, targetFile)
    CreateFromUsageBaseFix.startTemplate(newEditor, template, project, templateListener, null)
  }
}

private inline fun extractNames(suggestedNames: SuggestedNameInfo?, defaultName: () -> String): Array<out String> {
  val names = (suggestedNames ?: SuggestedNameInfo.NULL_INFO).names
  return if (names.isEmpty()) arrayOf(defaultName()) else names
}

private fun TemplateBuilder.setupParameterName(parameter: PsiParameter, names: Array<out String>) {
  val nameIdentifier = parameter.nameIdentifier ?: return
  val expression = ParameterNameExpression(names)
  replaceElement(nameIdentifier, expression)
}

private class MyMethodBodyListener(val project: Project, val editor: Editor, val file: PsiFile) : TemplateEditingAdapter() {

  override fun templateFinished(template: Template, brokenOff: Boolean) {
    if (brokenOff) return
    runWriteCommandAction(project) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.document)
      val offset = editor.caretModel.offset
      PsiTreeUtil.findElementOfClassAtOffset(file, offset - 1, PsiMethod::class.java, false)?.let { method ->
        setupMethodBody(method)
        setupEditor(method, editor)
      }
    }
  }
}
