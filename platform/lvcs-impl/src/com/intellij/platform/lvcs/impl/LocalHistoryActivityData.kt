// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.tree.RootEntry
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.ui.models.SelectionCalculator
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.platform.lvcs.impl.diff.getEntryPath
import com.intellij.util.SlowOperations

internal val AFFECTED_PATHS: Key<Collection<String>> = Key.create("Lvcs.Affected.Paths")

private val ROOT_ENTRY: Key<RootEntry> = Key.create("Lvcs.Root.Entry")
internal fun ActivityData.getRootEntry(gateway: IdeaGateway): RootEntry {
  return getOrCreateUserData(ROOT_ENTRY) {
    runReadAction {
      val affectedPaths = getUserData(AFFECTED_PATHS)
      if (!affectedPaths.isNullOrEmpty()) {
        return@runReadAction SlowOperations.knownIssue("IJPL-162340").use {
          gateway.createTransientRootEntryForPaths(affectedPaths, true)
        }
      }
      gateway.createTransientRootEntry()
    }
  }
}

private val SELECTION_CALCULATOR: Key<SelectionCalculator> = Key.create("Lvcs.Selection.Calculator")
internal fun ActivityData.getSelectionCalculator(facade: LocalHistoryFacade, gateway: IdeaGateway, scope: ActivityScope.Selection,
                                                 isOldContentUsed: Boolean): SelectionCalculator {
  return getOrCreateUserData(SELECTION_CALCULATOR) {
    val rootEntry = getRootEntry(gateway)
    val changeSets = items.filterIsInstance<ChangeSetActivityItem>().map { RevisionId.ChangeSet(it.id) }
    val entryPath = getEntryPath(gateway, scope)
    SelectionCalculator.create(facade, gateway, rootEntry, entryPath, listOf(RevisionId.Current) + changeSets,
                                      scope.from, scope.to, isOldContentUsed)
  }
}