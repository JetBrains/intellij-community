// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.platform.navbar.NavBarVmItem
import kotlinx.coroutines.flow.Flow

interface NavBarServiceDelegate {

  fun activityFlow(): Flow<Unit>

  suspend fun defaultModel(): NavBarVmItem

  suspend fun contextModel(ctx: DataContext): List<NavBarVmItem>

  suspend fun navigate(item: NavBarVmItem)
}
