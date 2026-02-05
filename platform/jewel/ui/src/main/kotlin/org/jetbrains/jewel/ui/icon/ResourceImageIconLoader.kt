// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.rendering.ImageResourceLoader
import org.jetbrains.jewel.foundation.InternalJewelApi

@InternalJewelApi
@ApiStatus.Internal
public class PathImageResourceLoader(public val path: String, public val classLoader: ClassLoader?) : ImageResourceLoader {
    public fun loadData(): ByteArray {
        if (classLoader != null) return classLoader.getResourceAsStream(path)!!.readBytes()
        return ClassLoader.getSystemResourceAsStream(path)!!.readBytes()
    }
}
