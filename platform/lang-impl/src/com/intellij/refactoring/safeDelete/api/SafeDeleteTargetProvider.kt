// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.api

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

interface SafeDeleteTargetProvider {
  fun createSafeDeleteTarget(element: PsiElement): SafeDeleteTarget?

  companion object {
    @ApiStatus.Internal
    private val EP_NAME: ExtensionPointName<SafeDeleteTargetProvider> = ExtensionPointName.create("com.intellij.safeDeleteTargetProvider")
    
    fun createSafeDeleteTarget(element: PsiElement) : SafeDeleteTarget? {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.createSafeDeleteTarget(element) }
    }
  }
}