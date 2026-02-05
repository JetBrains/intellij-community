// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.modifiers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.jetbrains.icons.ExperimentalIconsApi

/**
 * Modifications that should be performed on the Layer, like sizing, margin, color filters etc. (order-dependant)
 *
 * Sample usage:
 * '''
 * icon {
 *    image("icons/icon.svg", IconModifier.margin(20.px))
 * }
 * '''
 *
 * @see AlignIconModifier
 * @see MarginIconModifier
 * @see HeightIconModifier
 * @see WidthIconModifier
 */
@Serializable
@ExperimentalIconsApi
sealed interface IconModifier {
  companion object: IconModifier
}

/**
 * Concatenates this modifier with another.
 *
 * Returns a [IconModifier] representing this modifier followed by [other] in sequence.
 */
@ExperimentalIconsApi
infix fun IconModifier.then(other: IconModifier): IconModifier =
  if (other === IconModifier) this else CombinedIconModifier(this, other)