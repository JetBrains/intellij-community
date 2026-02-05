// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.layers

import org.jetbrains.icons.layers.IconLayer
import org.jetbrains.icons.modifiers.CombinedIconModifier
import org.jetbrains.icons.modifiers.IconModifier

internal inline fun <reified TModifier: IconModifier> IconLayer.findModifier(): TModifier? {
  var output: TModifier? = null
  traverseModifiers(modifier) {
    if (it is TModifier) {
      output = it
      return@traverseModifiers false
    }
    return@traverseModifiers true
  }
  return output
}

private fun traverseModifiers(modifier: IconModifier, traverser: (IconModifier) -> Boolean): Boolean {
  if (traverser(modifier)) {
    if (modifier is CombinedIconModifier) {
      if (traverseModifiers(modifier.other, traverser)) {
        return traverseModifiers(modifier.root, traverser)
      } else return false
    } else return true
  }
  return false
}
