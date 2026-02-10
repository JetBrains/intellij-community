// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable
import org.jetbrains.icons.DynamicIcon
import org.jetbrains.icons.Icon

@Serializable
open class DefaultDynamicIcon(
  internal val id: String,
  internal var currentIcon: DefaultLayeredIcon,
  @kotlinx.serialization.Transient
  private val updateListener: ((DynamicIcon, Icon) -> Unit)? = null
): DynamicIcon {
  @kotlinx.serialization.Transient
  private val updateFlow = MutableSharedFlow<Icon>()

  override fun getCurrentIcon(): Icon {
    return currentIcon
  }

  override suspend fun swap(icon: Icon) {
    if (icon !is DefaultLayeredIcon) error("Unsupported icon type: $icon")
    currentIcon = icon
    updateListener?.invoke(this, icon)
    updateFlow.emit(icon)
  }

  override fun getFlow(): Flow<Icon> {
    return updateFlow
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DefaultDynamicIcon

    if (id != other.id) return false
    if (currentIcon != other.currentIcon) return false

    return true
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + currentIcon.hashCode()
    return result
  }

  override fun toString(): String {
    return "IntelliJDynamicIcon(id='$id', currentIcon=$currentIcon)"
  }

}
