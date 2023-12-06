// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink

import com.intellij.codeInspection.dataFlow.CommonDataflow
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.impl.light.LightRecordMember

private val EP_NAME: ExtensionPointName<SourceToSinkProvider> = create("com.intellij.codeInspection.sourceToSinkProvider")

/**
 * Provides necessary data for source-to-sink analysis
 */
interface SourceToSinkProvider {
  companion object {
    @JvmField
    val sourceToSinkLanguageProvider = LanguageExtension<SourceToSinkProvider>(EP_NAME.name)
  }

  /**
   * Returns a physical element for a given light element.
   * Analog UElement.getSourcePsi()
   *
   * @param element the light or non-physical element
   * @return physical element corresponding to the light element, null if no physical element exists.
   */
  fun getPhysicalForLightElement(element: PsiElement?): PsiElement?

  fun computeConstant(element: PsiElement?): Any?
}

class JavaSourceToSinkProvider : SourceToSinkProvider {
  override fun getPhysicalForLightElement(element: PsiElement?): PsiElement? {
    //only records now
    return (element as? LightRecordMember)?.recordComponent
  }

  override fun computeConstant(element: PsiElement?): Any? {
    if (element is PsiExpression) return CommonDataflow.computeValue(element)
    return null
  }
}