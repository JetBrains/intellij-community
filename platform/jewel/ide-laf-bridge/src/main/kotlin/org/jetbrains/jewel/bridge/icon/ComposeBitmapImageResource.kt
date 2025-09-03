// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.icon

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.intellij.ui.icons.AwtImageResource
import org.jetbrains.icons.api.BitmapImageResource
import org.jetbrains.icons.api.ImageResourceWithCrossApiCache
import org.jetbrains.icons.api.cachedBitmap
import java.awt.image.BufferedImage

public fun BitmapImageResource.composeBitmap(): ImageBitmap {
    // TODO Check for compose image bitmap
    val cache = (this as? ImageResourceWithCrossApiCache)?.crossApiCache
    val imageGenerator = {
        val awtImage = this as? AwtImageResource
        val buffered = awtImage?.image as? BufferedImage
        buffered?.toComposeImageBitmap() ?: error("Cannot convert Image to Compose Image Bitmap, only BufferedImage is supported")
    }
    return cache?.cachedBitmap<ImageBitmap>(imageGenerator) ?: imageGenerator()
}
