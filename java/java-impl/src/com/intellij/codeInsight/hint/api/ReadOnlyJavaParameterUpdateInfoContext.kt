// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint.api

import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile


/**
 * Class is used to extract logic about preferred signature and current parameter without making any changes within the UI.
 */
internal class ReadOnlyJavaParameterUpdateInfoContext(private val file: PsiFile, private val candidates: Array<Any>?, private val offset: Int) : UpdateParameterInfoContext {
  private var myCurrentParameterIndex = -1
  private var myHighlightedParameter: Any? = null

  override fun removeHint() {}

  override fun setParameterOwner(o: PsiElement?) {}

  override fun getParameterOwner(): PsiElement? {
    throw UnsupportedOperationException()
  }

  override fun setHighlightedParameter(parameter: Any?) {
    myHighlightedParameter = parameter
  }

  override fun getHighlightedParameter(): Any? {
    return myHighlightedParameter
  }

  override fun setCurrentParameter(index: Int) {
    myCurrentParameterIndex = index
  }

  fun getHighlightedSignatureIndex(): Int? {
    val index = candidates?.indexOfFirst { it === myHighlightedParameter }
    return if (index == -1) null else index
  }

  fun getCurrentParameterIndex(): Int? {
    return if (myCurrentParameterIndex == -1) null else myCurrentParameterIndex
  }

  override fun isUIComponentEnabled(index: Int): Boolean = false

  override fun setUIComponentEnabled(index: Int, enabled: Boolean) = Unit

  override fun getParameterListStart(): Int = throw UnsupportedOperationException()

  override fun getObjectsToView(): Array<Any>? = candidates

  override fun isPreservedOnHintHidden(): Boolean = false

  override fun setPreservedOnHintHidden(value: Boolean) = Unit

  override fun isInnermostContext(): Boolean = false

  override fun isSingleParameterInfo(): Boolean = false

  override fun getCustomContext(): UserDataHolderEx = throw UnsupportedOperationException()

  override fun getProject(): Project = file.project

  override fun getFile(): PsiFile = file

  override fun getOffset(): Int = offset

  override fun getEditor(): Editor = throw UnsupportedOperationException()
}