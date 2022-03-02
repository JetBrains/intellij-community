// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search

class DefaultSearchEverywhereTabsCustomization: SearchEverywhereTabsCustomization {

  override fun getContributorsWithTab(): List<String> =
    listOf("ClassSearchEverywhereContributor", "FileSearchEverywhereContributor", "SymbolSearchEverywhereContributor",
           "ActionSearchEverywhereContributor", "DbSETablesContributor", "Vcs.Git")

}