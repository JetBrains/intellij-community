// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.StubBuilder
import org.jetbrains.annotations.ApiStatus

/**
 * Defines stub support for a given language.
 *
 * Register via `com.intellij.languageStubDefinition` extension point
 *
 * @see [LanguageStubDescriptor.stubDefinition], [LightLanguageStubDefinition]
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
interface LanguageStubDefinition {
  /**
   * Version of serialization format for the language.
   * You must update it every time you change the format.
   *
   * todo IJPL-562 get rid of template base version here???
   */
  val stubVersion: Int

  /**
   * StubBuilder of the language.
   */
  val builder: StubBuilder

  /**
   * You can customize if you need to build stubs for a given file
   */
  fun shouldBuildStubFor(file: VirtualFile): Boolean = true
}