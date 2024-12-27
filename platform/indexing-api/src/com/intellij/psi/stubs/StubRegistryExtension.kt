// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.ApiStatus

/**
 * Implement this interface in order to provide stub implementation for your language.
 * Register the implementation as an extension of `com.intellij.stubElementRegistryExtension` extension point.
 *
 * TODO IJPL-562 do we need to register individual stub factories and serializers in plugin.xml instead?
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
interface StubRegistryExtension {
  fun register(registry: StubRegistry)
}

/**
 * Implement [StubRegistryExtension] for your language and pass all necessary implementations to this registry in [StubRegistryExtension.register]
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface StubRegistry {
  fun registerStubFactory(type: IElementType, factory: StubElementFactory<*, *>)
  fun registerLightStubFactory(type: IElementType, factory: LightStubElementFactory<*, *>)
  fun registerStubSerializer(type: IElementType, serializer: ObjectStubSerializer<*, *>)
}
