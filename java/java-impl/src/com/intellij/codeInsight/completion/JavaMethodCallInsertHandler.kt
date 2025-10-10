// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.util.MethodParenthesesHandler
import com.intellij.codeInsight.hint.ParameterInfoControllerBase
import com.intellij.codeInsight.hint.ShowParameterInfoContext
import com.intellij.codeInsight.hint.api.impls.MethodParameterInfoHandler
import com.intellij.codeInsight.hints.ParameterHintsPass
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.PsiImplUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import kotlin.math.min

public open class JavaMethodCallInsertHandler<MethodCallElement : JavaMethodCallElement> : InsertHandler<MethodCallElement> {
  override fun handleInsert(context: InsertionContext, item: MethodCallElement) {
    val document = context.document
    val file = context.file
    val method = item.getObject()

    val allItems: Array<LookupElement> = context.elements
    val hasParams = if (method.getParameterList().isEmpty) ThreeState.NO else MethodParenthesesHandler.overloadsHaveParameters(allItems, method)
    if (method.isConstructor()) {
      val aClass = method.getContainingClass()
      if (aClass != null && aClass.getTypeParameters().size > 0) {
        document.insertString(context.tailOffset, "<>")
      }
    }
    JavaCompletionUtil.insertParentheses(context, item, false, hasParams, false)

    val offset = context.startOffset
    val refStart = context.trackOffset(offset, true)
    beforeHandle(context)
    if (item.isNeedExplicitTypeParameters) {
      qualifyMethodCall(item, file, context.getOffset(refStart), document)
      insertExplicitTypeParameters(item, context, refStart)
    }
    else if (item.helper != null) {
      context.commitDocument()
      importOrQualify(item, document, file, method, context.getOffset(refStart))
    }

    var methodCall = findCallAtOffset(context, context.getOffset(refStart))
    // make sure this is the method call we've just added, not the enclosing one
    if (methodCall != null) {
      val completedElement = (methodCall as? PsiMethodCallExpression)?.getMethodExpression()?.getReferenceNameElement()
      val completedElementRange = completedElement?.getTextRange()
      if (completedElementRange == null || completedElementRange.startOffset != context.getOffset(refStart)) {
        methodCall = null
      }
    }
    if (methodCall != null) {
      CompletionMemory.registerChosenMethod(method, methodCall)
      handleNegation(context, document, methodCall, item.isNegatable)
    }

    afterHandle(context, methodCall)

    if (canStartArgumentLiveTemplate()) {
      JavaMethodCallElement.startArgumentLiveTemplate(context, method)
    }
    showParameterHints(item, context, method, methodCall)
  }

  /**
   * Called before insertion methods. Performs any necessary pre-processing or setup.
   * 
   * @param context the insertion context for the code template, must not be null
   */
  protected open fun beforeHandle(context: InsertionContext) {
  }

  /**
   * Called after insertion methods. Performs any necessary post-processing or cleanup.
   * 
   * @param context the insertion context for the code template
   * @param call    the PsiCallExpression representing the inserted code, or null if no code was inserted
   */
  protected open fun afterHandle(context: InsertionContext, call: PsiCallExpression?) {
  }

  /**
   * Checks if the argument live template can be started.
   * see registry key java.completion.argument.live.template.description.
   * This option allows to prevent running templates if this key is enabled
   * 
   * @return true if the argument live template can be started, otherwise false.
   */
  protected open fun canStartArgumentLiveTemplate(): Boolean {
    return true
  }

  private fun importOrQualify(
    item: MethodCallElement,
    document: Document,
    file: PsiFile,
    method: PsiMethod,
    startOffset: Int
  ) {
    if (!needImportOrQualify()) {
      return
    }
    if (item.willBeImported()) {
      val containingClass = item.containingClass
      if (method.isConstructor()) {
        val newExpression = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiNewExpression::class.java, false)
        if (newExpression != null) {
          val ref = newExpression.getClassReference()
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

  /**
   * Determines if import or qualification is needed.
   * 
   * @return true if import or qualification is needed, false otherwise
   */
  protected open fun needImportOrQualify(): Boolean {
    return true
  }

  private fun qualifyMethodCall(item: MethodCallElement, file: PsiFile, startOffset: Int, document: Document) {
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
    item: MethodCallElement,
    context: InsertionContext,
    refStart: OffsetKey
  ) {
    context.commitDocument()

    val typeParams = JavaMethodCallElement.getTypeParamsText(false, item.getObject(), item.inferenceSubstitutor)
    if (typeParams != null) {
      context.document.insertString(context.getOffset(refStart), typeParams)
      JavaCompletionUtil.shortenReference(context.file, context.getOffset(refStart))
    }
  }

  private fun handleNegation(
    context: InsertionContext,
    document: Document,
    methodCall: PsiCallExpression,
    negatable: Boolean
  ) {
    if (context.completionChar == '!' && negatable) {
      context.setAddCompletionChar(false)
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH)
      document.insertString(methodCall.getTextRange().startOffset, "!")
    }
  }

  public companion object {
    @JvmStatic
    public fun findCallAtOffset(context: InsertionContext, offset: Int): PsiCallExpression? {
      context.commitDocument()
      return PsiTreeUtil.findElementOfClassAtOffset(context.file, offset, PsiCallExpression::class.java, false)
    }

    @JvmStatic
    public fun showParameterHints(
      element: LookupElement,
      context: InsertionContext,
      method: PsiMethod,
      methodCall: PsiCallExpression?
    ) {
      if (!CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION ||
          context.completionChar == Lookup.COMPLETE_STATEMENT_SELECT_CHAR ||
          context.completionChar == Lookup.REPLACE_SELECT_CHAR ||
          methodCall == null ||
          methodCall.getContainingFile() is PsiCodeFragment ||
          element.getUserData(JavaMethodMergingContributor.MERGED_ELEMENT) != null
      ) {
        return
      }
      val parameterList = method.getParameterList()
      val parametersCount = parameterList.getParametersCount()
      val parameterOwner = methodCall.getArgumentList()
      if (parameterOwner == null || (parameterOwner.getText() != "()") || parametersCount == 0) {
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
