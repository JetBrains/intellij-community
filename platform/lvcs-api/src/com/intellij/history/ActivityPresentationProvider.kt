// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

/**
 * Allows to provide presentation for the activities recorded in the local history.
 *
 * @see ActivityId
 */
@ApiStatus.Experimental
interface ActivityPresentationProvider {
  /**
   * Unique provider identifier.
   */
  val id: String

  /**
   * Icon to use for the activity presentation.
   * @param kind activity identifier
   */
  fun getIcon(kind: @NonNls String): Icon? = null
}