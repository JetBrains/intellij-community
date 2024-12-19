// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.SeTab
import com.intellij.platform.searchEverywhere.SeTabProvider
import fleet.kernel.DurableRef
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeActionsTabProvider : SeTabProvider {
  override suspend fun getTab(project: Project, sessionRef: DurableRef<SeSessionEntity>): SeTab = SeActionsTab.create(project, sessionRef)
}