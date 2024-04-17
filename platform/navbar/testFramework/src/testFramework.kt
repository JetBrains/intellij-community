// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.testFramework

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.platform.navbar.NavBarItemPresentation
import com.intellij.platform.navbar.backend.NavBarItem
import com.intellij.platform.navbar.backend.compatibility.DefaultNavBarItem
import com.intellij.platform.navbar.backend.compatibility.compatibilityNavBarItem
import com.intellij.platform.navbar.backend.compatibility.getBgData
import com.intellij.platform.navbar.backend.impl.children
import com.intellij.platform.navbar.backend.impl.pathToItem
import com.intellij.platform.navbar.frontend.contextModel
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.TestOnly

// a place for public test-only functions

/**
 * Use this API to dump current state of navigation bar in tests
 * Currently used in Rider
 */
@TestOnly
@Internal
suspend fun dumpContextModel(ctx: DataContext, project: Project): List<String> {
  return contextModel(ctx, project).map { it.presentation.text }
}

@TestOnly
@RequiresReadLock
fun contextNavBarPathStrings(ctx: DataContext): List<String> {
  val contextItem = NavBarItem.NAVBAR_ITEM_KEY.getData(ctx)
                    ?: return emptyList()
  return contextItem.pathToItem().map {
    it.presentation().text
  }
}

/**
 * Instead, instantiate the own implementation of [NavBarItem],
 * and test its [NavBarItem.presentation].
 */
@Obsolete
@TestOnly
@RequiresReadLock
fun compatibilityNavBarItemPresentation(o: Any): NavBarItemPresentation? {
  val item = compatibilityNavBarItem(o, null)
  return item?.presentation()
}

/**
 * Instead, instantiate the own implementation of [NavBarItem],
 * and test the own implementation of [NavBarItemProvider.findParent] against it.
 */
@Obsolete
@TestOnly
@RequiresReadLock
fun compatibilityNavBarPathObjects(o: Any): List<Any> {
  val item = compatibilityNavBarItem(o, null)
             ?: return emptyList()
  return item.pathToItem().mapNotNull {
    if (it is DefaultNavBarItem<*>) {
      it.data
    }
    else {
      null
    }
  }
}

/**
 * Instead, instantiate the own implementation of [NavBarItem],
 * and test the own implementation of [NavBarItemProvider.iterateChildren] against it.
 */
@Obsolete
@TestOnly
@RequiresReadLock
fun compatibilityNavBarChildObjects(o: Any): List<Any> {
  val item = compatibilityNavBarItem(o, null)
             ?: return emptyList()
  return item.children().mapNotNull {
    if (it is DefaultNavBarItem<*>) {
      it.data
    }
    else {
      null
    }
  }
}

/**
 * Instead, test own data rule implementation,
 * which is expected to provide some data by own [NavBarItem] in the data context.
 */
@Obsolete
@TestOnly
@RequiresReadLock
@Suppress("UNCHECKED_CAST")
fun <T> compatibilitySelectionData(project: Project, o: Any, key: DataKey<T>): T? {
  val item = compatibilityNavBarItem(o, null)
             ?: return null
  return getBgData(project, listOf(item.createPointer()), key.name) as T?
}
