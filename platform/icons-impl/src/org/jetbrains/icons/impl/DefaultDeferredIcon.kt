// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl

import kotlinx.serialization.Serializable
import org.jetbrains.icons.DeferredIcon
import org.jetbrains.icons.Icon

@Serializable
open class DefaultDeferredIcon(
  internal val id: String?,
  override val placeholder: Icon?,
): DeferredIcon {
  override val isDone: Boolean = false
  internal val currentIcon: Icon? = placeholder

}
