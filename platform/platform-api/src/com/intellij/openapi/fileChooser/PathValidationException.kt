// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Tells that a path validator rejects the chosen paths.
 * A validator of [NioFileChooserBuilder.withPathValidator] can throw this exception.
 * The file chooser then shows [message] to the user and stays open.
 *
 * Use this exception for an expected rejection with a localised message.
 * The file chooser also accepts other exceptions, but it logs them as unexpected.
 *
 * @param message the localised text that the file chooser shows to the user.
 */
@ApiStatus.Experimental
class PathValidationException(message: @Nls String) : Exception(message) {
  override val message: @Nls String
    get() = super.message!!
}
