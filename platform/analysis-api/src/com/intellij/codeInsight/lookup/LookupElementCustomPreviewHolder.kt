// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.extensions.ExtensionPointName
import jdk.jfr.Experimental
import org.jetbrains.annotations.ApiStatus

/**
 * Represents an entity that can provide or indicate the availability of a custom preview for a lookup element.
 *
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface LookupElementCustomPreviewHolder {
  val preview: IntentionPreviewInfo
}

/**
 * An internal interface used to determine if a given [Lookup] instance may have a custom preview.
 */
@Experimental
@ApiStatus.Internal
interface LookupMayHaveCustomPreviewProvider {
  companion object {
    val EP_NAME: ExtensionPointName<LookupMayHaveCustomPreviewProvider> = ExtensionPointName("com.intellij.lookup.may.have.custom.preview.provider")
  }

  fun mayHaveCustomPreview(lookup: Lookup): Boolean
}