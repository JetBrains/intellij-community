// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ImageResourceProvider
import javax.swing.Icon

abstract class DefaultImageResourceProvider : ImageResourceProvider {
    abstract fun fromSwingIcon(icon: Icon, imageModifiers: ImageModifiers? = null): ImageResource
}
