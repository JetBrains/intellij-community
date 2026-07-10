@file:ApiStatus.Internal
@file:JvmName("DeleteUtil")
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.ide.IdeBundle
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.PropertyKey

fun generateWarningMessage(
  key: @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String,
  elements: Array<PsiElement>
): @NlsContexts.DialogMessage String {
  if (elements.size == 1) {
    val name = ElementDescriptionUtil.getElementDescription(elements[0], DeleteNameDescriptionLocation.INSTANCE)
    val type = ElementDescriptionUtil.getElementDescription(elements[0], DeleteTypeDescriptionLocation.SINGULAR)
    return IdeBundle.message(key, type + (if (name.isBlank()) "" else " \"$name\""))
  }

  val countMap = hashMapOf<String, Int>().withDefault { 0 }
  val pluralToSingular = HashMap<String, String>()
  var directoryCount = 0
  var containerType: String? = null
  for (elementToDelete in elements) {
    val type = ElementDescriptionUtil.getElementDescription(elementToDelete, DeleteTypeDescriptionLocation.PLURAL)
    pluralToSingular[type] = ElementDescriptionUtil.getElementDescription(elementToDelete, DeleteTypeDescriptionLocation.SINGULAR)
    countMap[type] = countMap.getValue(type) + 1
    if (elementToDelete is PsiDirectoryContainer) {
      containerType = type
      directoryCount += elementToDelete.getDirectories().size
    }
  }

  val buffer = StringBuilder()
  for ((type, count) in countMap) {
    if (!buffer.isEmpty()) {
      buffer.append(" ").append(IdeBundle.message("prompt.delete.and")).append(" ")
    }
    buffer.append(count).append(" ")
    if (count == 1) {
      buffer.append(pluralToSingular.getValue(type))
    }
    else {
      buffer.append(type)
    }
    if (type == containerType) {
      buffer.append(" ").append(IdeBundle.message("prompt.delete.directory.paren", directoryCount))
    }
  }

  return IdeBundle.message(key, buffer.toString())
}
