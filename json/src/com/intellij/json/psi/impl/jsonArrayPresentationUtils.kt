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
  return JsonBundle.message("folding.collapsed.array.text", childrenCount)
}