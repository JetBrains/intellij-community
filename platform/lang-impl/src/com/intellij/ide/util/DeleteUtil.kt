// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.ide.IdeBundle
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import com.intellij.util.containers.FactoryMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.PropertyKey

@ApiStatus.Internal
object DeleteUtil {
  @NlsContexts.DialogMessage
  fun generateWarningMessage(
    @PropertyKey(resourceBundle = IdeBundle.BUNDLE) key: @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String,
    elements: Array<PsiElement>
  ): @NlsContexts.DialogMessage String {
    if (elements.size == 1) {
      val name = ElementDescriptionUtil.getElementDescription(elements[0], DeleteNameDescriptionLocation.INSTANCE)
      val type = ElementDescriptionUtil.getElementDescription(elements[0], DeleteTypeDescriptionLocation.SINGULAR)
      return IdeBundle.message(key, type + (if (StringUtil.isEmptyOrSpaces(name)) "" else " \"" + name + '"'))
    }

    val countMap = FactoryMap.create<String?, Int?>(Function { k: String? -> 0 })
    val pluralToSingular = HashMap<String?, String?>()
    var directoryCount = 0
    var containerType = null as String?
    for (elementToDelete in elements) {
      val type = ElementDescriptionUtil.getElementDescription(elementToDelete, DeleteTypeDescriptionLocation.PLURAL)
      pluralToSingular.put(
        type,
        ElementDescriptionUtil.getElementDescription(elementToDelete, DeleteTypeDescriptionLocation.SINGULAR)
      )
      val oldCount: Int = countMap.get(type)!!
      countMap.put(type, oldCount + 1)
      if (elementToDelete is PsiDirectoryContainer) {
        containerType = type
        directoryCount += elementToDelete.getDirectories().size
      }
    }

    val buffer = StringBuilder()
    for (entry in countMap.entries) {
      if (!buffer.isEmpty()) {
        buffer.append(" ").append(IdeBundle.message("prompt.delete.and")).append(" ")
      }

      val count: Int = entry.value!!
      buffer.append(count).append(" ")
      if (count == 1) {
        buffer.append(pluralToSingular.get(entry.key))
      } else {
        buffer.append(entry.key)
      }

      if (entry.key == containerType) {
        buffer.append(" ").append(IdeBundle.message("prompt.delete.directory.paren", directoryCount))
      }
    }

    return IdeBundle.message(key, buffer.toString())
  }
}
