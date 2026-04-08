// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layers

import com.intellij.ide.minimap.MinimapPanel
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.util.LinkedHashMap

@ApiStatus.OverrideOnly
interface MinimapLayerFactory {
  /**
   * Stable unique identifier for enable/disable and replacement semantics.
   */
  val id: MinimapLayerId

  /**
   * Paint order: smaller values are rendered first.
   */
  val order: Int

  fun isApplicable(panel: MinimapPanel): Boolean = true

  fun createLayer(panel: MinimapPanel): MinimapLayer

  companion object {
    val EP_NAME: ExtensionPointName<MinimapLayerFactory> = ExtensionPointName("com.intellij.minimapLayerFactory")

    private val LOG = logger<MinimapLayerFactory>()

    fun createLayers(panel: MinimapPanel): List<MinimapLayer> {
      val factoriesById = LinkedHashMap<MinimapLayerId, MinimapLayerFactory>()
      for (factory in EP_NAME.extensionList.sortedBy(MinimapLayerFactory::order)) {
        if (!factory.isApplicable(panel)) continue
        val previous = factoriesById.put(factory.id, factory)
        if (previous != null && previous !== factory) {
          LOG.warn("Duplicate minimap layer id '${factory.id.value}', replacing ${previous.javaClass.name} with ${factory.javaClass.name}")
        }
      }
      return factoriesById.values.map { it.createLayer(panel) }
    }
  }
}
