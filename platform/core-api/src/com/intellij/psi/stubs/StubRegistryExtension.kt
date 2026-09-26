// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * Implement this interface in order to provide stub implementation for your language.
 * Register the implementation as an extension of `com.intellij.stubElementRegistryExtension` extension point.
 */
@ApiStatus.OverrideOnly
interface StubRegistryExtension {
  @Contract(pure = true)
  fun register(registry: StubRegistry)
}

/**
 * Implement [StubRegistryExtension] for your language and pass all necessary implementations to this registry in [StubRegistryExtension.register]
 */
@ApiStatus.NonExtendable
interface StubRegistry {
  @Contract(pure = true)
  fun registerStubSerializingFactory(type: IElementType, factory: StubSerializingElementFactory<*, *>) {
    registerStubFactory(type, factory)
    registerStubSerializer(type, factory)
  }

  @Contract(pure = true)
  fun registerStubFactory(type: IElementType, factory: StubElementFactory<*, *>)

  @Contract(pure = true)
  fun registerLightStubFactory(type: IElementType, factory: LightStubElementFactory<*, *>)

  @Contract(pure = true)
  fun registerStubSerializer(type: IElementType, serializer: ObjectStubSerializer<*, *>)
}
