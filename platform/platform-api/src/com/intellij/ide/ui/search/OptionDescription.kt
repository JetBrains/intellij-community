// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

open class OptionDescription @JvmOverloads constructor(
  private val _option: @Nls String?,
  val configurableId: @NonNls String?,
  val hit: @NlsSafe String?,
  val path: @NlsSafe String?,
  val groupName: @Nls String? = null
) : Comparable<OptionDescription> {
  constructor(hit: @NlsSafe String?) : this(option = null, hit = hit, path = null)

  constructor(option: @Nls String?, hit: @NlsSafe String?, path: @NlsSafe String?) : this(option, null, hit, path)

  open val value: @NlsSafe String?
    get() = null

  open val option: @Nls String?
    get() = _option

  open fun hasExternalEditor(): Boolean = false

  open fun invokeInternalEditor() {
  }

  override fun toString(): String = hit ?: ""

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false

    val that = other as OptionDescription

    if (configurableId != that.configurableId) return false
    if (hit != that.hit) return false
    if (option != that.option) return false
    if (path != that.path) return false

    return true
  }

  override fun hashCode(): Int {
    var result = option?.hashCode() ?: 0
    result = 31 * result + (hit?.hashCode() ?: 0)
    result = 31 * result + (path?.hashCode() ?: 0)
    result = 31 * result + (configurableId?.hashCode() ?: 0)
    return result
  }

  override fun compareTo(other: OptionDescription): Int {
    val hit1 = hit ?: ""
    val hit2 = other.hit ?: ""
    val diff = hit1.compareTo(hit2)
    if (diff != 0) return diff

    val option1 = option
    val option2 = other.option
    if (option1 != null && option2 != null) {
      return option1.compareTo(option2)
    }
    else if (option1 != null || option2 != null) {
      // nulls go last
      return if (option1 == null) 1 else -1
    }
    else {
      return 0
    }
  }
}
