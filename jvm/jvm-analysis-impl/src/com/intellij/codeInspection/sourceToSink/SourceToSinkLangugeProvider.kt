// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.psi.PsiElement
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

  fun getPhysicalForLightElement(element: PsiElement?): PsiElement?
}

class JavaSourceToSinkProvider : SourceToSinkProvider {
  override fun getPhysicalForLightElement(element: PsiElement?): PsiElement? {
    //only records now
    return (element as? LightRecordMember)?.recordComponent
  }
}