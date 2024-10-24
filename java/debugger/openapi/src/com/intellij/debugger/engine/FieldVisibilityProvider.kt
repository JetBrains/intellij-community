/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    @JvmField
    val EP_NAME: ExtensionPointName<FieldVisibilityProvider> =
      ExtensionPointName.create("com.intellij.debugger.fieldVisibilityProvider")
  }
}