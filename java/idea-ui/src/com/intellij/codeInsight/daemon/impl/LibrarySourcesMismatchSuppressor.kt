// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiClass
import org.jetbrains.annotations.ApiStatus

/**
 * Suppress the notification that reports a mismatch between a library class and the source of the library.
 *
 * In some cases, the mismatch is expected, so the notification is a noise.
 */
@ApiStatus.Internal
interface LibrarySourcesMismatchSuppressor {

  /**
   * Returns `true` to suppress the notification for [sourceClass].
   *
   * [sourceClass] is the class in the library source. `sourceClass.originalElement` gives the compiled class.
   */
  fun shouldSuppress(sourceClass: PsiClass): Boolean

  companion object {

    @JvmField
    val EP_NAME: ExtensionPointName<LibrarySourcesMismatchSuppressor> =
      ExtensionPointName.create("com.intellij.librarySourcesMismatchSuppressor")

    fun shouldSuppress(sourceClass: PsiClass): Boolean {
      return EP_NAME.findFirstSafe { it.shouldSuppress(sourceClass) } != null
    }
  }
}
