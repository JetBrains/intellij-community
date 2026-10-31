// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.help

import org.jetbrains.annotations.ApiStatus

/**
 * Supplies product help after plugin-specific [WebHelpProvider] extensions have been checked.
 * Register an implementation as `com.intellij.helpTargetProvider`.
 */
@ApiStatus.Experimental
interface HelpTargetProvider {
  /** Returns a target, or null to use the next provider. This method must not perform network operations. */
  fun getHelpTarget(helpId: String): HelpTarget?
}

/** A help URL and the optional ID of the browser used when the IDE opens it. */
@ApiStatus.Experimental
class HelpTarget @JvmOverloads constructor(val url: String, val browserId: String? = null)
