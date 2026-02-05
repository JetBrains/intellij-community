// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.modifiers

import com.intellij.platform.icons.IconManager

/**
 * Modifications that should be performed on the Layer, like sizing, margin, color filters etc. (order-dependant)
 *
 * Sample usage: ''' icon { image("icons/icon.svg", IconModifier.margin(20.px)) } '''
 *
 * @see align
 * @see margin
 * @see scale
 */
interface IconModifier {
    companion object : IconModifier
}

/**
 * Concatenates this modifier with another.
 *
 * Returns a [IconModifier] representing this modifier followed by [other] in sequence.
 */
infix fun IconModifier.then(other: IconModifier): IconModifier =
    if (other === IconModifier) this else IconManager.modifiers().combine(this, other)
