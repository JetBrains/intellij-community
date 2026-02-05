// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.custom

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.rendering.ImageResourceLoader
import javax.swing.Icon
import kotlin.reflect.KClass

abstract class CustomImageResourceLoader<T : Icon>(
  klass: KClass<T>
): CustomSerializableRegistration<T>(klass) {
  @ApiStatus.Internal
  companion object: CustomSerializableRegistration.Companion<ImageResourceLoader, CustomImageResourceLoader<*>>(
    ImageResourceLoader::class,
    "com.intellij.customImageResourceLoader"
  )
}