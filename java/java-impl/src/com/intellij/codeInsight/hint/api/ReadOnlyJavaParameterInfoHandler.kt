// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint.api

import com.intellij.psi.PsiFile

/**
 * Represents the read-only API for getting parameter information. By read-only, it means that the handler
 * is able to return information about different signatures and doesn't modify any UI.
 */
public interface ReadOnlyJavaParameterInfoHandler {
  public fun getParameterInfo(file: PsiFile, offset: Int): JavaParameterInfo?
}