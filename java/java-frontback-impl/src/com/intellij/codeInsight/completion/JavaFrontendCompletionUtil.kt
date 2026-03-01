// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.AutoPopupControllerHelper
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.TailType
import com.intellij.codeInsight.TailTypes
import com.intellij.codeInsight.completion.util.CompletionStyleUtil
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler
import com.intellij.codeInsight.editorActions.TabOutScopesTracker
import com.intellij.codeInsight.lookup.EqTailType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupItem
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiForStatement
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ThreeState

object JavaFrontendCompletionUtil {
  /**
   * If used in a [FrontendFriendlyInsertHandler], make sure that the original backend item does not have [LookupItem.getTailType].
   * This tail type is going to be lost if used in the Remote Development scenario.
   */
  @JvmStatic
  fun insertParentheses(
    context: InsertionContext,
    item: LookupElement,
    overloadsMatter: Boolean,
    hasParams: ThreeState,  // UNSURE if providing no arguments is a valid situation
    forceClosingParenthesis: Boolean,
    isVoidMethod: Boolean,
  ) {
    var hasParams = hasParams
    val editor = context.editor
    val completionChar = context.completionChar
    val file = context.file

    val tailType = when (completionChar) {
      '(' -> TailTypes.noneType()
      ':' -> TailTypes.conditionalExpressionColonType()
      else -> LookupItem.handleCompletionChar(context.editor, item, completionChar)
    }

    val hasTail = tailType !== TailTypes.noneType() && tailType !== TailTypes.unknownType()
    val smart = completionChar == Lookup.COMPLETE_STATEMENT_SELECT_CHAR

    if (completionChar == '(' || completionChar == '.' || completionChar == ',' || completionChar == ';' || completionChar == ':' || completionChar == ' ') {
      context.setAddCompletionChar(false)
    }

    if (hasTail) {
      hasParams = ThreeState.NO
    }

    val needRightParenth = forceClosingParenthesis ||
                           !smart && (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET || hasParams == ThreeState.NO && completionChar != '(')

    context.commitDocument()

    val styleSettings = CompletionStyleUtil.getCodeStyleSettings(context)
    val elementAt = file.findElementAt(context.startOffset)
    if (elementAt == null || elementAt.parent !is PsiMethodReferenceExpression) {
      val hasParameters = hasParams
      val spaceBetweenParentheses = hasParams == ThreeState.YES && styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES ||
                                    hasParams == ThreeState.UNSURE && styleSettings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES

      val parenthesesInsertHandler = JavaParenthesesInsertHandler(
        spaceBeforeParentheses = styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES,
        spaceBetweenParentheses = spaceBetweenParentheses,
        mayInsertRightParenthesis = needRightParenth,
        allowParametersOnNextLine = styleSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE,
        hasParameters = hasParameters != ThreeState.NO
      )
      parenthesesInsertHandler.handleInsert(context, item)
    }

    if (hasParams != ThreeState.NO) {
      // Invoke parameters popup
      AutoPopupControllerHelper.getInstance(file.project).autoPopupParameterInfoAfterCompletion(
        editor = editor,
        selectedItem = if (overloadsMatter) null else item
      )
    }

    if (smart || !needRightParenth || !EditorSettingsExternalizable.getInstance().isInsertParenthesesAutomatically) {
      return
    }

    if (!insertTail(context, item, tailType, hasTail, isVoidMethod)) {
      return
    }

    when (completionChar) {
      '.' -> {
        AutoPopupController.getInstance(file.project).scheduleAutoPopup(context.editor)
      }
      ',' -> {
        AutoPopupController.getInstance(file.project).autoPopupParameterInfo(context.editor, null)
      }
    }
  }

  private fun insertTail(
    context: InsertionContext,
    item: LookupElement,
    tailType: TailType,
    hasTail: Boolean,
    isVoidMethod: Boolean,
  ): Boolean {
    var toInsert = tailType
    if (toInsert === EqTailType.INSTANCE) {
      toInsert = TailTypes.unknownType()
    }

    val lookupItem = item.`as`(LookupItem.CLASS_CONDITION_KEY)
    if (lookupItem == null || lookupItem.getAttribute(LookupItem.TAIL_TYPE_ATTR) !== TailTypes.unknownType()) {
      if (!hasTail && isVoidMethod) {
        PsiDocumentManager.getInstance(context.project).commitAllDocuments()
        if (PlatformPatterns.psiElement().beforeLeaf(PlatformPatterns.psiElement().withText(".")).accepts(context.file.findElementAt(context.tailOffset - 1))) {
          return false
        }

        var insertAdditionalSemicolon = true
        val leaf = context.file.findElementAt(context.startOffset)
        val composite = leaf?.parent
        if (composite is PsiReferenceExpression) {
          var parent = composite.parent
          if (parent is PsiMethodCallExpression) {
            parent = parent.parent
          }

          if (parent is PsiLambdaExpression && !insertSemicolonAfter(parent)) {
            insertAdditionalSemicolon = false
          }
          if (parent is PsiExpressionStatement && (parent.parent as? PsiForStatement)?.update === parent) {
            insertAdditionalSemicolon = false
          }
        }
        if (insertAdditionalSemicolon) {
          toInsert = TailTypes.semicolonType()
        }
      }
    }
    val editor = context.editor
    val tailOffset = context.tailOffset
    val afterTailOffset = toInsert.processTail(editor, tailOffset)
    val caretOffset = editor.caretModel.offset

    // todo IJPL-207762 what's this??? shall it run on frontend or backend???
    if (tailOffset in (caretOffset + 1)..<afterTailOffset && TabOutScopesTracker.getInstance().removeScopeEndingAt(editor, caretOffset) > 0) {
      TabOutScopesTracker.getInstance().registerEmptyScope(editor, caretOffset, afterTailOffset)
    }

    return true
  }

  @JvmStatic
  fun insertSemicolonAfter(lambdaExpression: PsiLambdaExpression): Boolean {
    return lambdaExpression.body is PsiCodeBlock || insertSemicolon(lambdaExpression.parent)
  }

  @JvmStatic
  fun insertSemicolon(parent: PsiElement?): Boolean {
    return parent !is PsiExpressionList && parent !is PsiExpression
  }
}

private class JavaParenthesesInsertHandler(
  spaceBeforeParentheses: Boolean,
  spaceBetweenParentheses: Boolean,
  mayInsertRightParenthesis: Boolean,
  allowParametersOnNextLine: Boolean,
  val hasParameters: Boolean,
) : ParenthesesInsertHandler<LookupElement>(spaceBeforeParentheses, spaceBetweenParentheses, mayInsertRightParenthesis, allowParametersOnNextLine) {

  override fun placeCaretInsideParentheses(context: InsertionContext?, item: LookupElement): Boolean = hasParameters

  override fun findExistingLeftParenthesis(context: InsertionContext): PsiElement? {
    val token = super.findExistingLeftParenthesis(context)
    return if (isPartOfLambda(token)) null else token
  }

  private fun isPartOfLambda(token: PsiElement?): Boolean {
    return token != null &&
           token.parent is PsiExpressionList &&
           PsiUtilCore.getElementType(PsiTreeUtil.nextVisibleLeaf(token.parent)) === JavaTokenType.ARROW
  }
}