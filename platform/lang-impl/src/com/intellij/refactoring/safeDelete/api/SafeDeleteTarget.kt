// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.api

import com.intellij.model.Pointer
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.search.SearchScope

interface SafeDeleteTarget {
  fun createPointer(): Pointer<out SafeDeleteTarget>

  fun targetPresentation() : TargetPresentation
  
  fun declarations() : Collection<PsiSafeDeleteDeclarationUsage>
  
  override fun equals(other : Any?) : Boolean
  override fun hashCode() : Int

  val searchScope: SearchScope?
    get() = null
}