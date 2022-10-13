// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.impl

import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.NavBarItemProvider
import com.intellij.openapi.application.ApplicationManager

internal fun NavBarItem.pathToItem(): List<NavBarItem> {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  return generateSequence(this) {
    it.findParent()
  }.toList().asReversed()
}

private fun NavBarItem.findParent(): NavBarItem? =
  NavBarItemProvider.EP_NAME
    .extensionList
    .firstNotNullOfOrNull { ext -> ext.findParent(this) }
