// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.backend.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.platform.navbar.NavBarItemPresentationData
import com.intellij.platform.navbar.backend.NavBarItem
import com.intellij.platform.navbar.backend.NavBarItemProvider

fun NavBarItem.pathToItem(): List<NavBarItem> {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  return generateSequence(this) {
    ProgressManager.checkCanceled()
    it.findParent()
  }.toList().asReversed()
}

private fun NavBarItem.findParent(): NavBarItem? {
  for (ext in NavBarItemProvider.EP_NAME.extensionList) {
    return ext.findParent(this)
           ?: continue
  }
  return null
}

fun NavBarItem.children(): List<NavBarItem> {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  return iterateAllChildren().sortedWith(siblingsComparator)
}

private fun NavBarItem.iterateAllChildren(): Iterable<NavBarItem> =
  NavBarItemProvider.EP_NAME
    .extensionList
    .flatMap { ext -> ext.iterateChildren(this) }

private val weightComparator = compareBy<NavBarItem> { -it.weight() }
private val nameComparator = compareBy<NavBarItem, String>(NaturalComparator.INSTANCE) {
  (it.presentation() as NavBarItemPresentationData).text
}
private val siblingsComparator = weightComparator.then(nameComparator)
