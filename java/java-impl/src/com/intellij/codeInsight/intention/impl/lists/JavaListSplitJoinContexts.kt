// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.lists

import com.intellij.application.options.CodeStyle
import com.intellij.java.JavaBundle
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.actions.lists.CommaListSplitJoinContext
import com.intellij.openapi.editor.actions.lists.JoinOrSplit
import com.intellij.openapi.editor.actions.lists.ListWithElements
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil


public abstract class AbstractJavaSplitJoinContext : CommaListSplitJoinContext() {
  override fun isValidIntermediateElement(data: ListWithElements, element: PsiElement): Boolean {
    return super.isValidIntermediateElement(data, element) ||
           element is PsiComment && element.tokenType === JavaTokenType.C_STYLE_COMMENT
  }
}

public class JavaSplitJoinArgumentsContext : AbstractJavaSplitJoinContext() {
  override fun extractData(context: PsiElement): ListWithElements? =
    getCallArgumentsList(context)?.let { ListWithElements(it, it.expressions.asList()) }

  private fun getCallArgumentsList(element: PsiElement): PsiExpressionList? {
    val expressionList = PsiTreeUtil.getParentOfType(element, PsiExpressionList::class.java, false, PsiCodeBlock::class.java) ?: return null
    val parent = expressionList.parent
    return if (parent !is PsiCall) null else expressionList
  }

  override fun needHeadBreak(data: ListWithElements, firstElement: PsiElement, mode: JoinOrSplit): Boolean {
    if (mode == JoinOrSplit.SPLIT) {
      //todo check, looks weired, copied from the original impl
      return CodeStyle.getLanguageSettings(firstElement.containingFile, JavaLanguage.INSTANCE).CALL_PARAMETERS_RPAREN_ON_NEXT_LINE
    }
    return CodeStyle.getLanguageSettings(firstElement.containingFile, JavaLanguage.INSTANCE).CALL_PARAMETERS_LPAREN_ON_NEXT_LINE
  }

  override fun needTailBreak(data: ListWithElements, lastElement: PsiElement, mode: JoinOrSplit): Boolean =
    CodeStyle.getLanguageSettings(lastElement.containingFile, JavaLanguage.INSTANCE).CALL_PARAMETERS_RPAREN_ON_NEXT_LINE

  override fun getJoinText(data: ListWithElements): String = JavaBundle.message("intention.family.put.arguments.on.one.line")
  override fun getSplitText(data: ListWithElements): String = JavaBundle.message("intention.family.put.arguments.on.separate.lines")
}

public class JavaSplitJoinParametersContext : AbstractJavaSplitJoinContext() {
  override fun extractData(context: PsiElement): ListWithElements? =
    PsiTreeUtil.getParentOfType(context, PsiParameterList::class.java, false)?.let { ListWithElements(it, it.parameters.toList()) }

  override fun needHeadBreak(data: ListWithElements, firstElement: PsiElement, mode: JoinOrSplit): Boolean =
    CodeStyle.getLanguageSettings(firstElement.containingFile, JavaLanguage.INSTANCE).METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE

  override fun needTailBreak(data: ListWithElements, lastElement: PsiElement, mode: JoinOrSplit): Boolean =
    CodeStyle.getLanguageSettings(lastElement.containingFile, JavaLanguage.INSTANCE).METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE

  override fun getSplitText(data: ListWithElements): String = JavaBundle.message("intention.family.put.parameters.on.separate.lines")
  override fun getJoinText(data: ListWithElements): String = JavaBundle.message("intention.family.put.parameters.on.one.line")
}

public class JavaSplitJoinRecordComponentsContext : AbstractJavaSplitJoinContext() {
  override fun extractData(context: PsiElement): ListWithElements? =
    PsiTreeUtil.getParentOfType(context, PsiRecordHeader::class.java, false, PsiCodeBlock::class.java, PsiExpression::class.java)
      ?.let { ListWithElements(it, it.recordComponents.toList()) }

  override fun needHeadBreak(data: ListWithElements, firstElement: PsiElement, mode: JoinOrSplit): Boolean =
    when (mode) {
      JoinOrSplit.JOIN -> false
      JoinOrSplit.SPLIT -> true
    }

  override fun needTailBreak(data: ListWithElements, lastElement: PsiElement, mode: JoinOrSplit): Boolean =
    when (mode) {
      JoinOrSplit.JOIN -> false
      JoinOrSplit.SPLIT -> true
    }

  override fun getSplitText(data: ListWithElements): String = JavaBundle.message("intention.family.put.record.components.on.separate.lines")
  override fun getJoinText(data: ListWithElements): String = JavaBundle.message("intention.family.put.record.components.on.one.line")
}