// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser.elements

import com.intellij.ide.plugins.parser.elements.ClientKind.Companion.convert
import com.intellij.ide.plugins.parser.elements.OS.Companion.convert
import com.intellij.ide.plugins.parser.elements.ServiceElement.PreloadMode.Companion.convert
import com.intellij.openapi.components.ServiceDescriptor

class ServiceElement(
  @JvmField val serviceInterface: String?,
  @JvmField val serviceImplementation: String?,
  @JvmField val testServiceImplementation: String?,
  @JvmField val headlessImplementation: String?,
  @JvmField val overrides: Boolean,
  @JvmField val configurationSchemaKey: String?,
  @JvmField val preload: PreloadMode,
  @JvmField val client: ClientKind?,
  @JvmField val os: OS?
) {
  enum class PreloadMode {
    TRUE, FALSE, AWAIT, NOT_HEADLESS, NOT_LIGHT_EDIT;

    companion object {
      fun PreloadMode.convert(): ServiceDescriptor.PreloadMode = when (this) {
        TRUE -> ServiceDescriptor.PreloadMode.TRUE
        FALSE -> ServiceDescriptor.PreloadMode.FALSE
        AWAIT -> ServiceDescriptor.PreloadMode.AWAIT
        NOT_HEADLESS -> ServiceDescriptor.PreloadMode.NOT_HEADLESS
        NOT_LIGHT_EDIT -> ServiceDescriptor.PreloadMode.NOT_LIGHT_EDIT
        else -> throw IllegalArgumentException("Unknown preload mode: $this")
      }
    }
  }

  companion object {
    // TODO move out
    fun ServiceElement.convert(): ServiceDescriptor = ServiceDescriptor(
      serviceInterface,
      serviceImplementation,
      testServiceImplementation,
      headlessImplementation,
      overrides,
      configurationSchemaKey,
      preload.convert(),
      client?.convert(),
      os?.convert()
    )
  }
}
