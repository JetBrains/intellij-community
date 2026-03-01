// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.TailTypes
import com.intellij.codeInsight.completion.JavaMethodCallElement.areParameterTemplatesEnabledOnCompletion
import com.intellij.codeInsight.completion.JavaMethodCallInsertHandler.Companion.needParameterHints
import com.intellij.codeInsight.completion.JavaMethodCallInsertHandler.Companion.showParameterHints
import com.intellij.codeInsight.completion.method.DiamondInsertHandler
import com.intellij.codeInsight.completion.method.FrontendFriendlyParenthesesInsertHandler
import com.intellij.codeInsight.completion.method.JavaMethodCallInsertHandlerHelper
import com.intellij.codeInsight.completion.method.JavaMethodCallInsertHandlerHelper.findInsertedCall
import com.intellij.codeInsight.completion.method.MethodCallInstallerHandler
import com.intellij.codeInsight.completion.method.NegationInsertHandler
import com.intellij.codeInsight.completion.method.RefStartInsertHandler
import com.intellij.codeInsight.completion.serialization.InsertHandlerSerializer
import com.intellij.codeInsight.completion.util.MethodParenthesesHandler
import com.intellij.codeInsight.hint.ParameterInfoControllerBase
import com.intellij.codeInsight.hint.ShowParameterInfoContext
import com.intellij.codeInsight.hint.api.impls.MethodParameterInfoHandler
import com.intellij.codeInsight.hints.ParameterHintsPass
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypes
import com.intellij.psi.impl.PsiImplUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import kotlin.math.min

public class JavaMethodCallInsertHandler(
  needExplicitTypeParameters: Boolean,

  /**
   * Called before insertion methods. Performs any necessary pre-processing or setup.
   */
  beforeHandler: InsertHandler<JavaMethodCallElement>? = null,

  /**
   * Called after insertion methods. Performs any necessary post-processing or cleanup.
   *
   * Use [findInsertedCall] to get PsiCallExpression representing the inserted code, or null if no code was inserted
   */
  afterHandler: InsertHandler<JavaMethodCallElement>? = null,

  /**
   * Determines if import or qualification is needed.
   *
   * @return true if import or qualification is needed, false otherwise
   */
  needImportOrQualify: Boolean = true,

  /**
   * Checks if the argument live template can be started.
   * see registry key java.completion.argument.live.template.description.
   * This option allows to prevent running templates if this key is enabled
   *
   * true if the argument live template can be started, otherwise false.
   */
  canStartArgumentLiveTemplate: Boolean = true,

  /**
   * The element to be handled.
   * It is expected that the element is fully set up and ready for insertion.
   */
  item: JavaMethodCallElement,
) : InsertHandler<JavaMethodCallElement> {

  /**
   * tracks the start offset of the reference, needs movableToRight=true to correctly handle insertion of a qualifier
   */
  private val myHandlers: List<InsertHandler<in JavaMethodCallElement>> = listOfNotNull(
    RefStartInsertHandler(),
    createDiamondInsertHandler(item),
    MethodCallParenthesesInsertHandler.create(item),
    beforeHandler,
    ImportQualifyAndInsertTypeParametersHandler.create(needImportOrQualify, needExplicitTypeParameters, item),
    MethodCallInstallerHandler(),
    MethodCallRegistrationHandler(),
    createNegationInsertHandler(item),
    afterHandler,
    ArgumentLiveTemplateInsertHandler.create(canStartArgumentLiveTemplate),
    ShowParameterInfoInsertHandler.create(item),
  )

  override fun handleInsert(context: InsertionContext, item: JavaMethodCallElement) {
    myHandlers.forEach { it.handleInsert(context, item) }
  }

  internal class Converter : InsertHandlerToFrontendFriendlyConverter<JavaMethodCallInsertHandler> {
    override fun toDescriptor(target: JavaMethodCallInsertHandler): FrontendFriendlyInsertHandler? {
      val convertedChildren = target.myHandlers.mapNotNull { child ->
        InsertHandlerSerializer.toDescriptor(child)
      }

      if (convertedChildren.size != target.myHandlers.size) return null

      val operational = convertedChildren.filterNot { it is NoOpFrontendFriendlyInsertHandler }

      return CompositeFrontendFriendlyInsertHandler("JavaMethodCallInsertHandler#frontendFriendly", operational)
    }
  }

  public companion object {
    internal fun needParameterHints(element: LookupElement, method: PsiMethod): Boolean {
      if (!CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION ||
          element.getUserData(JavaMethodMergingContributor.MERGED_ELEMENT) != null
      ) {
        return false
      }

      val parameterList = method.parameterList
      val parametersCount = parameterList.parametersCount
      return parametersCount != 0
    }

    @JvmStatic
    public fun showParameterHints(
      element: LookupElement,
      context: InsertionContext,
      method: PsiMethod,
      methodCall: PsiCallExpression?,
    ) {
      if (!needParameterHints(element, method) ||
          context.completionChar == Lookup.COMPLETE_STATEMENT_SELECT_CHAR ||
          context.completionChar == Lookup.REPLACE_SELECT_CHAR ||
          methodCall == null ||
          methodCall.containingFile is PsiCodeFragment
      ) {
        return
      }

      val parametersCount = method.parameterList.parametersCount
      assert(parametersCount != 0) { "must be checked in needParameterHints"}

      val parameterOwner = methodCall.argumentList
      if ((parameterOwner == null) || (parameterOwner.getText() != "()")) {
        return
      }

      val editor = context.editor
      if (editor is EditorWindow) return

      val project = context.project
      val document = editor.getDocument()
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)

      val limit = JavaMethodCallElement.getCompletionHintsLimit()

      val caretModel = editor.getCaretModel()
      val offset = caretModel.offset

      val afterParenOffset = offset + 1
      if (afterParenOffset < document.textLength && Character.isJavaIdentifierPart(document.getImmutableCharSequence()[afterParenOffset])) {
        return
      }

      val braceOffset = offset - 1
      val numberOfParametersToDisplay = if (parametersCount > 1 && PsiImplUtil.isVarArgs(method)) parametersCount - 1 else parametersCount
      val numberOfCommas = min(numberOfParametersToDisplay, limit) - 1
      val commas = if (Registry.`is`("editor.completion.hints.virtual.comma")) "" else StringUtil.repeat(", ", numberOfCommas)
      document.insertString(offset, commas)

      PsiDocumentManager.getInstance(project).commitDocument(document)
      val handler = MethodParameterInfoHandler()
      val infoContext = ShowParameterInfoContext(editor, project, context.file, offset, braceOffset)
      if (!methodCall.isValid() || handler.findElementForParameterInfo(infoContext) == null) {
        document.deleteString(offset, offset + commas.length)
        return
      }

      JavaMethodCallElement.setCompletionMode(methodCall, true)
      context.laterRunnable = Runnable {
        val itemsToShow = infoContext.itemsToShow
        val methodCallArgumentList = methodCall.getArgumentList()
        val controller =
          ParameterInfoControllerBase.createParameterInfoController(project, editor, braceOffset, itemsToShow, null,
                                                                    methodCallArgumentList, handler, false, false)
        val hintsDisposal = Disposable { JavaMethodCallElement.setCompletionMode(methodCall, false) }
        if (Disposer.isDisposed(controller)) {
          Disposer.dispose(hintsDisposal)
          document.deleteString(offset, offset + commas.length)
        }
        else {
          ParameterHintsPass.asyncUpdate(methodCall, editor)
          Disposer.register(controller, hintsDisposal)
        }
      }
    }
  }
}

private fun createDiamondInsertHandler(item: JavaMethodCallElement): InsertHandler<LookupElement>? {
  val method = item.getObject().takeIf { it.isConstructor } ?: return null
  val containingClass = method.containingClass ?: return null
  if (containingClass.typeParameters.isEmpty()) return null
  return DiamondInsertHandler()
}

private class MethodCallParenthesesInsertHandler private constructor(
  private val hasParameters: Boolean,
  private val hasTailType: Boolean,
  private val isVoidMethod: Boolean,
) : InsertHandler<JavaMethodCallElement> {
  override fun handleInsert(context: InsertionContext, item: JavaMethodCallElement) {
    val method = item.getObject()
    val allItems = context.elements
    val hasParams = if (hasParameters) MethodParenthesesHandler.overloadsHaveParameters(allItems, method) else ThreeState.NO
    FrontendFriendlyParenthesesInsertHandler.insertParenthesesForJavaMethod(item, context, hasParams, isVoidMethod)
  }

  class Converter : InsertHandlerToFrontendFriendlyConverter<MethodCallParenthesesInsertHandler> {
    override fun toDescriptor(target: MethodCallParenthesesInsertHandler): FrontendFriendlyInsertHandler? {
      if (target.hasTailType) return null
      return FrontendFriendlyParenthesesInsertHandler(target.hasParameters, target.isVoidMethod)
    }
  }

  companion object {
    fun create(item: JavaMethodCallElement): InsertHandler<JavaMethodCallElement> {
      val method = item.getObject()
      val hasTailType = item.tailType != TailTypes.unknownType()
      val isVoidMethod = method.returnType == PsiTypes.voidType()
      return MethodCallParenthesesInsertHandler(!method.parameterList.isEmpty, hasTailType, isVoidMethod)
    }
  }
}

private class ImportQualifyAndInsertTypeParametersHandler private constructor(
  private val needImportOrQualify: Boolean,
  private val needExplicitTypeParameters: Boolean,
) : InsertHandler<JavaMethodCallElement> {

  companion object {
    fun create(
      needImportOrQualify: Boolean,
      needExplicitTypeParameters: Boolean,
      item: JavaMethodCallElement,
    ): InsertHandler<JavaMethodCallElement>? {
      if (!needExplicitTypeParameters && item.helper == null) return null
      return ImportQualifyAndInsertTypeParametersHandler(needImportOrQualify, needExplicitTypeParameters)
    }
  }

  override fun handleInsert(context: InsertionContext, item: JavaMethodCallElement) {
    val document = context.document
    val file = context.file
    val method = item.getObject()

    if (needExplicitTypeParameters) {
      qualifyMethodCall(item, file, JavaMethodCallInsertHandlerHelper.getReferenceStartOffset(context, item), document)
      insertExplicitTypeParameters(item, context)
    }
    else if (item.helper != null) {
      context.commitDocument()
      importOrQualify(item, document, file, method, JavaMethodCallInsertHandlerHelper.getReferenceStartOffset(context, item))
    }
  }

  private fun importOrQualify(
    item: JavaMethodCallElement,
    document: Document,
    file: PsiFile,
    method: PsiMethod,
    startOffset: Int,
  ) {
    if (!needImportOrQualify) {
      return
    }
    if (item.willBeImported()) {
      val containingClass = item.containingClass
      if (method.isConstructor()) {
        val newExpression = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiNewExpression::class.java, false)
        if (newExpression != null) {
          val ref = newExpression.classReference
          if (ref != null && containingClass != null && !ref.isReferenceTo(containingClass)) {
            ref.bindToElement(containingClass)
            return
          }
        }
      }
      else {
        val ref = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiReferenceExpression::class.java, false)
        if (ref != null && containingClass != null && !ref.isReferenceTo(method)) {
          ref.bindToElementViaStaticImport(containingClass)
        }
        return
      }
    }

    qualifyMethodCall(item, file, startOffset, document)
  }

  private fun qualifyMethodCall(item: JavaMethodCallElement, file: PsiFile, startOffset: Int, document: Document) {
    val reference = file.findReferenceAt(startOffset)
    if (reference is PsiReferenceExpression && reference.isQualified()) {
      return
    }

    val method = item.getObject()
    if (method.isConstructor()) return
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      document.insertString(startOffset, "this.")
      return
    }

    val containingClass = item.containingClass ?: return

    document.insertString(startOffset, ".")
    JavaCompletionUtil.insertClassReference(containingClass, file, startOffset)
  }

  private fun insertExplicitTypeParameters(
    item: JavaMethodCallElement,
    context: InsertionContext,
  ) {
    context.commitDocument()

    val typeParams = JavaMethodCallElement.getTypeParamsText(false, item.getObject(), item.inferenceSubstitutor)
    if (typeParams != null) {
      context.document.insertString(JavaMethodCallInsertHandlerHelper.getReferenceStartOffset(context, item), typeParams)
      JavaCompletionUtil.shortenReference(context.file, JavaMethodCallInsertHandlerHelper.getReferenceStartOffset(context, item))
    }
  }
}

private fun createNegationInsertHandler(item: JavaMethodCallElement): InsertHandler<in JavaMethodCallElement>? {
  if (!item.isNegatable) return null
  return NegationInsertHandler()
}

private class ArgumentLiveTemplateInsertHandler : InsertHandler<JavaMethodCallElement> {
  override fun handleInsert(context: InsertionContext, item: JavaMethodCallElement) {
    val method = item.getObject()
    JavaMethodCallElement.startArgumentLiveTemplate(context, method)
  }

  companion object {
    fun create(canStartArgumentLiveTemplate: Boolean): InsertHandler<in JavaMethodCallElement>? {
      if (!canStartArgumentLiveTemplate || !areParameterTemplatesEnabledOnCompletion()) return null
      return ArgumentLiveTemplateInsertHandler()
    }
  }
}

private class ShowParameterInfoInsertHandler : InsertHandler<JavaMethodCallElement> {
  override fun handleInsert(context: InsertionContext, item: JavaMethodCallElement) {
    val method = item.getObject()
    val methodCall = findInsertedCall(item, context)
    showParameterHints(item, context, method, methodCall)
  }

  companion object {
    fun create(item: JavaMethodCallElement): InsertHandler<in JavaMethodCallElement>? {
      if (!needParameterHints(item, item.`object`)) return null
      return ShowParameterInfoInsertHandler()
    }
  }
}

private class MethodCallRegistrationHandler : InsertHandler<JavaMethodCallElement> {
  override fun handleInsert(context: InsertionContext, item: JavaMethodCallElement) {
    val method = item.getObject()
    val methodCall = findInsertedCall(item, context) ?: return
    CompletionMemory.registerChosenMethod(method, methodCall)
  }

  class Converter : InsertHandlerToFrontendFriendlyConverter<MethodCallRegistrationHandler> {
    override fun toDescriptor(target: MethodCallRegistrationHandler): FrontendFriendlyInsertHandler? {
      //TODO IJPL-207762 registering functionality is off on frontend, can be implemented separately if needed
      return NoOpFrontendFriendlyInsertHandler
    }
  }
}

