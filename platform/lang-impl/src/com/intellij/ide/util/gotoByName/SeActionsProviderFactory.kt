// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.api.SeItemsProvider
import com.intellij.platform.searchEverywhere.api.SeItemsProviderFactory

class SeActionsProviderFactory: SeItemsProviderFactory {
  override fun getItemsProvider(project: Project): SeItemsProvider = SeActionsProvider()
}