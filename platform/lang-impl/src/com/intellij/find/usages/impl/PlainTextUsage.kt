// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer

internal class PlainTextUsage(private val textUsage: TextUsage) : PsiUsage by textUsage {

  override fun createPointer(): Pointer<out PsiUsage> {
    return Pointer.delegatingPointer(textUsage.createPointer(), javaClass, ::PlainTextUsage)
  }
}
