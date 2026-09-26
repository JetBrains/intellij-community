// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.daemon.impl.quickfix.GuessTypeParameters
import com.intellij.codeInsight.daemon.impl.quickfix.JavaCreateFieldFromUsageHelper
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.lang.java.request.CreateFieldFromJavaUsageRequest
import com.intellij.lang.jvm.JvmLong
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateFieldActionGroup
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLambdaExpressionType
import com.intellij.psi.PsiLambdaParameterType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.JavaElementKind
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.psi.util.createSmartPointer

internal class CreateFieldAction(target: PsiClass, request: CreateFieldRequest) : CreateFieldActionBase(target, request) {

  override fun getActionGroup(): JvmActionGroup = CreateFieldActionGroup

  override fun getText(target: PsiClass): String = message("create.element.in.class", JavaElementKind.FIELD.`object`(),
                                                            request.fieldName, getNameForClass(target, false))
}

internal val constantModifiers: Set<JvmModifier> = setOf(
  JvmModifier.STATIC,
  JvmModifier.FINAL
)

/**
 * @param targetClass the writable copy of the target class
 * @param originalTarget the physical target class, to find the field again after the template ends
 */
internal class JavaFieldRenderer(
  private val project: Project,
  private val constantField: Boolean,
  private val targetClass: PsiClass,
  originalTarget: PsiClass,
  private val request: CreateFieldRequest,
  private val updater: ModPsiUpdater,
) {

  private val helper = JavaCreateFieldFromUsageHelper() // TODO get rid of it
  private val javaUsage = request as? CreateFieldFromJavaUsageRequest
  private val expectedTypes = extractExpectedTypes(project, request.fieldType, targetClass).toTypedArray()
  private val originalTargetPointer = originalTarget.createSmartPointer(project)

  private val modifiersToRender: Collection<JvmModifier>
    get() {
      return if (constantField) {
        // render static final explicitly
        request.modifiers + constantModifiers
      }
      else {
        // render as is
        request.modifiers
      }
    }

  fun doRender() {
    // Take every writable copy before the first write, as ModPsiUpdater.getWritable demands.
    val anchor = updater.getWritable(javaUsage?.anchor)
    val guesserContext = updater.getWritable(javaUsage?.reference)

    var field = renderField()
    field = insertField(field, anchor)
    // An annotation of the request carries the qualified name of its class. A copy of the file gets no
    // postponed formatting, which shortens such a name in a physical file. So shorten it here, and get
    // the import too.
    val codeStyleManager = JavaCodeStyleManager.getInstance(project)
    field.annotations.forEach { annotation -> codeStyleManager.shortenClassReferences(annotation) }
    if (request.fieldType.isEmpty() || request.fieldType.size > 1 || request.isStartTemplate) {
      startTemplate(field, guesserContext)
    }
  }

  fun renderField(): PsiField {
    var fieldType = if (expectedTypes.isNotEmpty()) expectedTypes[0].type else PsiTypes.intType()
    //something completely broken in this file, let's propose default value - Object
    if (fieldType is PsiLambdaParameterType ||
        fieldType is PsiLambdaExpressionType ||
        TypeConversionUtil.isNullType(fieldType)) fieldType = PsiType.getJavaLangObject(targetClass.manager, targetClass.getResolveScope())
    val field = JavaPsiFacade.getElementFactory(project).createField(request.fieldName, fieldType)

    // clean template modifiers
    field.modifierList?.let { list ->
      list.firstChild?.let {
        list.deleteChildRange(it, list.lastChild)
      }
    }

    // setup actual modifiers
    for (modifier in modifiersToRender.map(JvmModifier::toPsiModifier)) {
      PsiUtil.setModifierProperty(field, modifier, true)
    }

    field.modifierList?.let { modifierList ->
      for (annRequest in request.annotations) {
        modifierList.addAnnotation(annRequest.qualifiedName)
      }
    }

    val requestInitializer = request.initializer
    if (requestInitializer is JvmLong) {
      field.initializer = PsiElementFactory.getInstance(project).createExpressionFromText("${requestInitializer.longValue}L", null)
    }

    return field
  }

  private fun insertField(field: PsiField, anchor: PsiElement?): PsiField {
    return helper.insertFieldImpl(targetClass, field, anchor)
  }

  /**
   * A port of `JavaCreateFieldFromUsageHelper.setupTemplateImpl` onto [com.intellij.modcommand.ModTemplateBuilder].
   */
  private fun startTemplate(field: PsiField, guesserContext: PsiElement?) {
    val factory = JavaPsiFacade.getElementFactory(project)
    val typeElement = field.typeElement ?: return
    val substitutor = request.targetSubstitutor.toPsiSubstitutor(project)

    // Every PSI change happens first, and the template fields go in at the end. A ModTemplateBuilder
    // writes the value of a field into the document at once, which leaves the PSI behind, so an
    // offset read after that is stale.
    val fields = RecordedTemplateFields(project)
    GuessTypeParameters(project, factory, fields::field, substitutor)
      .setupTypeElement(typeElement, expectedTypes, guesserContext, targetClass)

    var initializer: PsiExpression? = null
    if (constantField && !field.hasInitializer()) {
      // The initializer holds a placeholder, and the empty template field clears it.
      field.initializer = factory.createExpressionFromText("0", null)
      initializer = field.initializer
    }

    val builder = updater.templateBuilder()
    if (initializer != null) {
      field.nameIdentifier.let { builder.finishAt(it.textRange.endOffset) }
      fields.field(initializer, ConstantNode(""))
    }
    fields.flushTo(builder)

    builder.onTemplateFinished { _ -> reformatField() }
  }

  /**
   * The template can leave the field badly formatted, so format it again and put the caret at its end.
   */
  private fun reformatField(): ModCommand {
    val originalTarget = originalTargetPointer.element ?: return ModCommand.nop()
    val field = originalTarget.findFieldByName(request.fieldName, false) ?: return ModCommand.nop()
    return ModCommand.psiUpdate(field) { writableField, fieldUpdater ->
      val formatted = CodeStyleManager.getInstance(project).reformat(writableField)
      // Put the caret in front of the closing semicolon, so the user can type the initializer.
      fieldUpdater.moveCaretTo(formatted.lastChild ?: formatted)
    }
  }
}
