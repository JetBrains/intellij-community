// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.inspector

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

class PropertyBean @JvmOverloads constructor(
  val propertyName: @NlsSafe String,
  val propertyValue: Any?,
  val changed: Boolean = false,
) {

  companion object {
    @ApiStatus.Internal
    fun compare(bean1: PropertyBean, bean2: PropertyBean): Boolean {
      return bean1.propertyValue == bean2.propertyValue
             && bean1.propertyValue == bean2.propertyValue
             && bean1.changed == bean2.changed
    }
  }
}
