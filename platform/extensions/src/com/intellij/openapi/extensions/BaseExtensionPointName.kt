// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import org.jetbrains.annotations.NonNls

abstract class BaseExtensionPointName<T : Any>(val name: @NonNls String) {
  override fun toString(): String = name

  @PublishedApi
  internal fun getPointImpl(areaInstance: AreaInstance?): ExtensionPointImpl<T> {
    val area = (areaInstance?.extensionArea ?: Extensions.getRootArea()) as ExtensionsAreaImpl
    return area.getExtensionPoint(name)
  }
}