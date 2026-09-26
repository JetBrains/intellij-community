// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.workspaceSymbol

import com.intellij.ide.actions.searcheverywhere.AbstractEqualityProvider
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.platform.lsp.api.customization.LspWorkspaceSymbolNavigationItem

/**
 * Equality provider for LSP workspace symbols to handle duplicate filtering in Search Everywhere.
 *
 * This provider unwraps presentation wrappers and compares the actual LspWorkspaceSymbolNavigationItem
 */
internal class LspWorkspaceSymbolEqualityProvider : AbstractEqualityProvider() {
  override fun areEqual(
    newItem: SearchEverywhereFoundElementInfo,
    alreadyFoundItem: SearchEverywhereFoundElementInfo,
  ): Boolean {
    val newElement = PSIPresentationBgRendererWrapper.getItem(newItem.element)
    val oldElement = PSIPresentationBgRendererWrapper.getItem(alreadyFoundItem.element)

    // Compare only if both are LSP workspace symbol navigation items
    return newElement is LspWorkspaceSymbolNavigationItem &&
           oldElement is LspWorkspaceSymbolNavigationItem &&
           newElement == oldElement
  }
}