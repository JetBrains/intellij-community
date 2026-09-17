// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.psi.PsiClass

internal class AndroidSdkSourcesMismatchSuppressor : LibrarySourcesMismatchSuppressor {

  // Support releases (e.g. "android-30") as well as previews (e.g. "android-tiramisu")
  private val ANDROID_SDK_PATTERN = ".*/platforms/android-\\w+/android.jar!/.*".toRegex()

  override fun shouldSuppress(sourceClass: PsiClass): Boolean {
    val offenderClsFile = sourceClass.originalElement.containingFile?.virtualFile ?: return false
    return offenderClsFile.path.matches(ANDROID_SDK_PATTERN)
  }
}
