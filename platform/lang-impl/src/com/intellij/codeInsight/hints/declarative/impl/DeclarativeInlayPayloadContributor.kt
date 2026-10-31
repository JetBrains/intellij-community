// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.InlayPayload
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/** Adds a custom payload to a declarative inlay hint while the hints are collected. */
@ApiStatus.Internal
interface DeclarativeInlayPayloadContributor {
  /**
   * The sink calls this function once for each collected hint.
   * It runs on the collection thread for every hint, so keep it fast.
   *
   * **Warning:** [activeOptionIds] is not immutable. It is the live state of the sink.
   * Treat it as a read-only view. Do not change it, and do not keep a reference to it.
   * The sink changes its content after this call returns. To use the IDs later, copy them, for example with `toList()`.
   *
   * @param activeOptionIds the option IDs, with one entry for each enclosing `whenOptionEnabled` block,
   * from the outermost to the innermost.
   * @return the payload to add to the hint, or `null`.
   */
  fun contribute(activeOptionIds: List<String>): InlayPayload?

  companion object {
    val EP_NAME: ExtensionPointName<DeclarativeInlayPayloadContributor> =
      ExtensionPointName("com.intellij.codeInsight.declarativeInlayPayloadContributor")
  }
}
