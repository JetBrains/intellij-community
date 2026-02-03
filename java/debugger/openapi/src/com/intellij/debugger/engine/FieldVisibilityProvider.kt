// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.openapi.extensions.ExtensionPointName
import com.sun.jdi.Field

/**
 * Allows plugins to hide fields unconditionally
 *
 * The field will be displayed only if all extensions allow.
 */
interface FieldVisibilityProvider {
  fun shouldDisplay(field: Field): Boolean

  companion object {
    private val EP_NAME: ExtensionPointName<FieldVisibilityProvider> = ExtensionPointName.create("com.intellij.debugger.fieldVisibilityProvider")

    @JvmStatic
    fun shouldDisplayField(field: Field): Boolean = EP_NAME.findFirstSafe { !it.shouldDisplay(field) } == null
  }
}