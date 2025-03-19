// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.ApiStatus

/**
 * Interface giving access to registered stub implementations.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface StubElementRegistryService {
  fun getStubFactory(type: IElementType): StubElementFactory<*, *>?

  // todo IJPL-562 do we need a separate method for LightStubElementFactory (which extends StubElementFactory)?
  fun getLightStubFactory(type: IElementType): LightStubElementFactory<*, *>?

  fun getStubSerializer(type: IElementType): ObjectStubSerializer<*, Stub>?

  fun getStubDescriptor(language: Language): LanguageStubDescriptor?

  companion object {
    @JvmStatic
    fun getInstance(): StubElementRegistryService = service()
  }
}
