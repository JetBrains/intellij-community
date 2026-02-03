// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.Unmodifiable

interface ExtensionsArea {
  @TestOnly
  fun registerExtensionPoint(extensionPointName: @NonNls String,
                             extensionPointBeanClass: String,
                             kind: ExtensionPoint.Kind,
                             isDynamic: Boolean)


  @TestOnly
  @Deprecated(message = "Do not use", replaceWith = ReplaceWith("registerExtensionPoint(String, String, ExtensionPoint.Kind, boolean)"))
  fun registerExtensionPoint(extensionPointName: @NonNls String,
                             extensionPointBeanClass: String,
                             kind: ExtensionPoint.Kind) {
    registerExtensionPoint(extensionPointName = extensionPointName,
                           extensionPointBeanClass = extensionPointBeanClass,
                           kind = kind,
                           isDynamic = false)
  }

  @TestOnly
  fun unregisterExtensionPoint(extensionPointName: @NonNls String)

  fun hasExtensionPoint(extensionPointName: @NonNls String): Boolean

  fun hasExtensionPoint(extensionPointName: ExtensionPointName<*>): Boolean

  fun <T : Any> getExtensionPoint(extensionPointName: @NonNls String): ExtensionPoint<T>

  fun <T : Any> getExtensionPointIfRegistered(extensionPointName: String): ExtensionPoint<T>?

  fun <T : Any> getExtensionPoint(extensionPointName: ExtensionPointName<T>): ExtensionPoint<T>

  @get:Internal
  val nameToPointMap: @Unmodifiable Map<String, ExtensionPointImpl<*>>

  @TestOnly
  @Internal
  fun processExtensionPoints(consumer: (ExtensionPointImpl<*>) -> Unit)
}
