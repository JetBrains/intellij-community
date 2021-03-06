// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus

/**
 * @author peter
 */
@Deprecated("Use LookupElementPresentation directly")
@ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
data class PresentationInvariant(val itemText: String?, val tail: String?, val type: String?): Comparable<PresentationInvariant> {
  override fun compareTo(other: PresentationInvariant): Int {
    var result = StringUtil.naturalCompare(itemText, other.itemText)
    if (result != 0) return result

    result = (tail?.length ?: 0).compareTo(other.tail?.length ?: 0)
    if (result != 0) return result

    result = StringUtil.naturalCompare(tail ?: "", other.tail ?: "")
    if (result != 0) return result

    return StringUtil.naturalCompare(type ?: "", other.type ?: "")
  }

}