// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.usageView.UsageInfo

internal class PsiUsage2UsageInfo(psiUsage: PsiUsage) : UsageInfo(psiUsage.file, psiUsage.range, psiUsage is PlainTextUsage) {

  private val pointer: Pointer<out PsiUsage> = psiUsage.createPointer()

  override fun isValid(): Boolean = super.isValid() && pointer.dereference() != null

  val psiUsage: PsiUsage get() = requireNotNull(pointer.dereference())
}
