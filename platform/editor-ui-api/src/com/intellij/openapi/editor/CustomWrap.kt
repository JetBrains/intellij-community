// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface CustomWrap : UserDataHolder {
  val offset: Int

  /** number of columns */
  val indent: Int

  val priority: Int
}