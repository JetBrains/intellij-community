// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.api

import com.intellij.model.Pointer
import com.intellij.usages.impl.rules.UsageType

interface Usage {

  fun createPointer(): Pointer<out Usage>

  // TODO decouple type from usage with extension Usage -> UsageType
  // TODO consider introducing separate UsageType to avoid dependency on intellij.platform.usageView
  @JvmDefault
  val usageType: UsageType?
    get() = null
}
