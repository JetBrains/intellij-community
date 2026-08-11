package com.intellij.platform.lsp.impl.connector

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
interface LspMessageFixRegexProvider {
  /** `null` means the fix is skipped entirely; a non-null value replaces the default regex. */
  val messageToFixRegex: Regex?
}
