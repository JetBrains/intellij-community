// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.actions

import com.intellij.ide.impl.dataRules.GetDataRule
import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.NavBarItemProvider
import com.intellij.ide.navbar.impl.*
import com.intellij.openapi.actionSystem.CommonDataKeys.*
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.MODULE
import com.intellij.openapi.module.ModuleType
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore


internal class DefaultNavBarItemDataRule : GetDataRule {

  override fun getData(dataProvider: DataProvider): NavBarItem? {
    val ctx = DataContext { dataId -> dataProvider.getData(dataId) }

    // leaf element -- either from old EP impls or default one
    // owner -- EP extension provided the leaf (if any)
    val (leaf, owner) = fromOldExtensions { ext -> ext.getLeafElement(ctx)?.let { it to ext } }
                        ?: fromDataContext(ctx)?.let { Pair(it, null) }
                        ?: return null

    if (leaf.isValid) {
      return PsiNavBarItem(leaf, owner)
    }
    else {
      // Narrow down the root element to the first interesting one
      MODULE.getData(ctx)
        ?.takeUnless { ModuleType.isInternal(it) }
        ?.let { return ModuleNavBarItem(it) }

      val projectItem = PROJECT.getData(ctx)
                          ?.let(::ProjectNavBarItem)
                        ?: return null

      val childItem = NavBarItemProvider.EP_NAME
                        .extensionList.asSequence()
                        .flatMap { ext -> ext.iterateChildren(projectItem) }
                        .firstOrNull()
                      ?: return projectItem

      val grandChildItem = NavBarItemProvider.EP_NAME
                             .extensionList.asSequence()
                             .flatMap { ext -> ext.iterateChildren(childItem) }
                             .firstOrNull()
                           ?: return childItem

      return grandChildItem
    }
  }

  private fun fromDataContext(ctx: DataContext): PsiElement? {
    val element = PSI_FILE.getData(ctx)
                  ?: PsiUtilCore.findFileSystemItem(PROJECT.getData(ctx), VIRTUAL_FILE.getData(ctx))
    return adjustWithAllExtensions(element)
  }

}
