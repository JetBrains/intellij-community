// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import org.jetbrains.annotations.NonNls

sealed class BaseExtensionPointName<T : Any>(val name: @NonNls String) {
  override fun toString(): String = name

  internal fun getPointImpl(areaInstance: AreaInstance?): ExtensionPointImpl<T> {
    val area = requireNotNull(areaInstance?.extensionArea ?: Extensions.getRootArea()) {
      """
        Can't get extension point. If you're running a JUnit5 test, make sure the test class is annotated with `@TestApplication`.
        Check out `com.intellij.testFramework.junit5.showcase.JUnit5ApplicationTest` for an example.
        """.trimIndent()
    } as ExtensionsAreaImpl
    return area.getExtensionPoint(name)
  }
}