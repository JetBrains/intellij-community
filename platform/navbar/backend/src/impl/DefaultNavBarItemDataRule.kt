// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.backend.impl

import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.openapi.actionSystem.CommonDataKeys.*
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.MODULE
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.openapi.module.ModuleType
import com.intellij.platform.navbar.backend.NavBarItem
import com.intellij.platform.navbar.backend.NavBarItem.Companion.NAVBAR_ITEM_KEY
import com.intellij.platform.navbar.backend.NavBarItemProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore

class DefaultNavBarItemDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    sink.lazyValue(NAVBAR_ITEM_KEY) { dataProvider ->
      getNavBarItem(dataProvider)?.createPointer()
    }
  }

  fun getNavBarItem(dataProvider: DataMap): NavBarItem? {
    // leaf element -- either from old EP impls or default one
    // owner -- EP extension provided the leaf (if any)
    val (leaf, owner) = fromOldExtensions { ext -> ext.getLeafElement(dataProvider)?.let { it to ext } }
                        ?: fromDataContext(dataProvider)?.let { Pair(it, null) }
                        ?: return null

    if (leaf.isValid) {
      if (PsiUtilCore.getVirtualFile(leaf)
            ?.getUserData(NavBarModelExtension.IGNORE_IN_NAVBAR) == true) {
        return null
      }
      return PsiNavBarItem(leaf, owner)
    }
    else {
      // Narrow down the root element to the first interesting one
      dataProvider[MODULE]
        ?.takeUnless { ModuleType.isInternal(it) }
        ?.let { return ModuleNavBarItem(it) }

      val projectItem = dataProvider[PROJECT]
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

  private fun fromDataContext(dataProvider: DataMap): PsiElement? {
    val psiFile = dataProvider[PSI_FILE]
    if (psiFile != null) {
      ensurePsiFromExtensionIsValid(psiFile, "Context PSI_FILE is invalid", psiFile.javaClass)
      return adjustWithAllExtensions(psiFile)
    }
    val fileSystemItem = PsiUtilCore.findFileSystemItem(dataProvider[PROJECT], dataProvider[VIRTUAL_FILE])
    if (fileSystemItem != null) {
      ensurePsiFromExtensionIsValid(fileSystemItem, "Context fileSystemItem is invalid", fileSystemItem.javaClass)
      return adjustWithAllExtensions(fileSystemItem)
    }
    return null
  }
}
