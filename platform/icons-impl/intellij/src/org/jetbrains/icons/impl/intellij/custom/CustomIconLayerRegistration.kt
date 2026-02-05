// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.custom

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.layers.IconLayer
import kotlin.reflect.KClass

abstract class CustomIconLayerRegistration<T : IconLayer>(
  klass: KClass<T>
): CustomSerializableRegistration<T>(klass) {
  @ApiStatus.Internal
  companion object: CustomSerializableRegistration.Companion<IconLayer, CustomImageResourceLoader<*>>(
    IconLayer ::class,
    "com.intellij.customIconLayer"
  )
}