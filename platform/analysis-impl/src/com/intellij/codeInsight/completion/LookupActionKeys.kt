// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object LookupActionKeys {
  @JvmField
  val SUPPRESS_QUICK_DEFINITION: Key<Boolean> = Key.create("lookup.suppress.quick.definition")

  @JvmField
  val SUPPRESS_QUICK_DOCUMENTATION: Key<Boolean> = Key.create("lookup.suppress.quick.documentation")
}
