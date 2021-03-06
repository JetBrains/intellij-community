// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.api

import com.intellij.model.Pointer
import com.intellij.usages.impl.rules.UsageType

/**
 * Usages include both declarations and references.
 *
 * @see PsiUsage
 */
interface Usage {

  fun createPointer(): Pointer<out Usage>

  /**
   * Whether this usage is a declaration (`true`).
   * Other (`false`) usages may include references, text usages, model usages, etc.
   */
  val declaration: Boolean

  // TODO decouple type from usage with extension Usage -> UsageType
  // TODO consider introducing separate UsageType to avoid dependency on intellij.platform.usageView
  @JvmDefault
  val usageType: UsageType?
    get() = null
}
