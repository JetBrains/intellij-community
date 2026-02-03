// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.ui.DeferredIcon

/**
 * Computes the mask of deferred icon components.
 *
 * The returned mask contains bits corresponding to deferred icons inside the given composite icon,
 * with each bit set if and only if the corresponding icon is not yet evaluated.
 *
 * If the icon contains composite icons, its components are also included in the resulting mask, recursively.
 */
internal fun deferredMask(icon: CompositeIcon): Int {
  return deferredMask(icon, 0L).mask
}

private fun deferredMask(icon: CompositeIcon, initialMask: Long): Long {
  // use a Long as a poor man's structure to avoid producing unnecessary garbage
  var mask = initialMask.mask
  var count = initialMask.count
  for (i in 0 until icon.iconCount) {
    when (val component = icon.getIcon(i)) {
      is CompositeIcon -> {
        val componentResult = deferredMask(component, mask(mask, count))
        val componentMask = componentResult.mask
        val componentCount = componentResult.count
        if (count + componentCount > 32) {
          LOG.warn("Icon has too many nested components, likely a bug in the icon structure: $icon")
          break
        }
        mask = mask or (componentMask shl count)
        count += componentCount
      }
      is DeferredIcon -> {
        if (count + 1 > 32) {
          LOG.warn("Icon has too many components, likely a bug in the icon structure: $icon")
          break
        }
        if (!component.isDone) {
          mask = mask or (1 shl count)
        }
        ++count
      }
    }
  }
  return mask(mask, count)
}

private val Long.mask: Int get() = toInt()
private val Long.count: Int get() = (this ushr 32).toInt()

private fun mask(mask: Int, count: Int): Long = (count.toLong() shl 32) or (mask.toLong() and 0xFF_FF_FF_FFL)

private val LOG = fileLogger()
