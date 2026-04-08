// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.legacy

import com.intellij.ide.minimap.geometry.MinimapGeometryData
import com.intellij.openapi.editor.Editor
import java.awt.Graphics2D
import java.lang.ref.SoftReference

class MinimapLegacyPreview(private val onImageReady: () -> Unit) {
  private var minimapImageSoftReference = SoftReference<MinimapImage>(null)

  fun paint(graphics: Graphics2D, editor: Editor, panelWidth: Int, geometry: MinimapGeometryData) {
    val minimap = getOrCreateImage()
    minimap.update(editor,
                   editor.contentComponent.height,
                   editor.scrollingModel.visibleArea.width,
                   geometry.minimapHeight)

    val preview = minimap.preview ?: return
    val scaleY = (preview.graphics as Graphics2D).transform.scaleY

    graphics.drawImage(preview, 0, 0, panelWidth, geometry.areaEnd - geometry.areaStart,
                       0, (geometry.areaStart * scaleY).toInt(), preview.width, (geometry.areaEnd * scaleY).toInt(),
                       null)
  }

  fun update(editor: Editor, minimapHeight: Int, force: Boolean) {
    minimapImageSoftReference.get()?.update(editor,
                                            editor.contentComponent.height,
                                            editor.scrollingModel.visibleArea.width,
                                            minimapHeight,
                                            force)
  }

  fun clear() {
    minimapImageSoftReference.clear()
  }

  private fun getOrCreateImage(): MinimapImage {
    var map = minimapImageSoftReference.get()
    if (map == null) {
      map = MinimapImage()
      map.onImageReady = onImageReady
      minimapImageSoftReference = SoftReference(map)
    }
    return map
  }
}