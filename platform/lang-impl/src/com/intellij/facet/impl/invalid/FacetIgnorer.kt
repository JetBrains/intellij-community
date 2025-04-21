// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl.invalid

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Temporary hack to ignore invalid facets.
 * When plugin that created certain facet hasn't been loaded, this facet is considered [InvalidFacet] and user asked to ignore it.
 * With [isIgnored] [EP] might ignore it unconditionally without bothering the user.
 */
@ApiStatus.Internal
interface FacetIgnorer {
  companion object {
    private val EP = ExtensionPointName.create<FacetIgnorer>("com.intellij.facetIgnorer")

    @JvmStatic
    fun isIgnoredByAnyEP(facet: InvalidFacet): Boolean = EP.extensionList.any { it.isIgnored(facet) }
  }

  fun isIgnored(facet: InvalidFacet): Boolean
}