// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.lang.Language
import com.intellij.psi.tree.IFileElementType
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a descriptor for language-specific stub implementations.
 * It summarizes information necessary for stub serialization.
 * Most of the information can be accessed via other API.
 *
 * See [StubElementRegistryService.getStubDescriptor]
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface LanguageStubDescriptor {
  /**
   * The language of this descriptor
   */
  val language: Language

  /**
   * The file-element type corresponding to this language.
   *
   */
  val fileElementType: IFileElementType

  /**
   * Stub definition corresponding to this language
   */
  val stubDefinition: LanguageStubDefinition

  /**
   * Serializer corresponding to the file-element type of this language.
   * `StubElementRegistryService.getInstance().getStubSerializer(this.fileElementType)` returns the same instance.
   */
  val fileElementSerializer: ObjectStubSerializer<*, *>
}
