// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.modcommand.ActionContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.util.ConcurrencyUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Represents an entity that can provide or indicate the availability of a custom preview for a lookup element.
 * @see LookupElement
 */
@ApiStatus.Experimental
interface LookupElementCustomPreviewHolder {
  fun hasPreview(): Boolean = true

  fun preview(ctx: ActionContext): IntentionPreviewInfo
}

/**
 * An internal interface used to determine if a given [Lookup] instance may have a custom preview.
 */
@ApiStatus.Internal
interface LookupMayHaveCustomPreviewProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<LookupMayHaveCustomPreviewProvider> = ExtensionPointName("com.intellij.lookup.may.have.custom.preview.provider")

    fun mayHaveCustomPreview(lookup: Lookup): Boolean =
      EP_NAME.findFirstSafe { it.mayHaveCustomPreview(lookup) } != null
  }

  fun mayHaveCustomPreview(lookup: Lookup): Boolean
}

private val previewCacheKey = Key.create<IntentionPreviewInfo>("preview.cache")

@ApiStatus.Internal
fun UserDataHolder.cachePreview(block: () -> IntentionPreviewInfo): IntentionPreviewInfo {
  return ConcurrencyUtil.computeIfAbsent(this, previewCacheKey) { block() }
}