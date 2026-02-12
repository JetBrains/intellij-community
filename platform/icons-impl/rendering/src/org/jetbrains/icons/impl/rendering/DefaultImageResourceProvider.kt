// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import org.jetbrains.icons.rendering.ImageModifiers
import org.jetbrains.icons.rendering.ImageResource
import org.jetbrains.icons.rendering.ImageResourceProvider

abstract class DefaultImageResourceProvider: ImageResourceProvider {
  abstract fun fromSwingIcon(icon: javax.swing.Icon, imageModifiers: ImageModifiers? = null): ImageResource
}