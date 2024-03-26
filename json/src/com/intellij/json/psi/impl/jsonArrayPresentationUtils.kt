// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JsonCollectionPsiPresentationUtils")

package com.intellij.json.psi.impl

import com.intellij.json.JsonBundle
import com.intellij.json.psi.JsonArray
import org.jetbrains.annotations.Nls

internal fun getCollectionPsiPresentationText(array: JsonArray): @Nls String {
  val childrenCount = array.valueList.size
  return getCollectionPsiPresentationText(childrenCount)
}

fun getCollectionPsiPresentationText(childrenCount: Int): @Nls String {
  return if (childrenCount % 10 == 1 && childrenCount % 100 != 11) {
    JsonBundle.message("folding.collapsed.array.one.element.text", childrenCount, SINGULAR_FORM)
  }
  else {
    JsonBundle.message("folding.collapsed.array.one.element.text", childrenCount, PLURAL_FORM)
  }
}

private const val SINGULAR_FORM = 1
private const val PLURAL_FORM = 2