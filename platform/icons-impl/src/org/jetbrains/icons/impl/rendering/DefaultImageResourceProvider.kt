// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import org.jetbrains.icons.rendering.ImageModifiers
import org.jetbrains.icons.rendering.ImageResource
import org.jetbrains.icons.rendering.ImageResourceProvider
import javax.swing.Icon

abstract class DefaultImageResourceProvider: ImageResourceProvider {
  abstract fun fromSwingIcon(icon: Icon, imageModifiers: ImageModifiers? = null): ImageResource
}