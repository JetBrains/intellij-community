// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.usages.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PlainTextUsage internal constructor(private val psiUsage: PsiUsage) : PsiUsage by psiUsage {

  override fun createPointer(): Pointer<out PsiUsage> {
    return Pointer.delegatingPointer(psiUsage.createPointer(), ::PlainTextUsage)
  }
}
