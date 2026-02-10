// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.custom

import com.intellij.ui.scale.ScaleContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.ImageResourceLoader
import org.jetbrains.icons.rendering.ImageModifiers
import java.awt.Image
import kotlin.reflect.KClass

abstract class CustomImageResourceLoader<T : ImageResourceLoader>(
  klass: KClass<T>
): CustomSerializableRegistration<T>(klass) {
  abstract fun loadImage(loader: T, scale: ScaleContext, imageModifiers: ImageModifiers?): Image?

  @ApiStatus.Internal
  companion object: CustomSerializableRegistration.Companion<ImageResourceLoader, CustomImageResourceLoader<*>>(
    ImageResourceLoader::class,
    "com.intellij.customImageResourceLoader"
  )
}