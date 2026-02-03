// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.branch

import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.util.NlsActions
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

class DvcsBranchSettings : BaseState() {
  @get:ApiStatus.Internal
  @set:ApiStatus.Internal
  @get:Tag("favorite-branches")
  var favorites by property(BranchStorage())

  @get:ApiStatus.Internal
  @set:ApiStatus.Internal
  @get:Tag("excluded-from-favorite")
  var excludedFavorites by property(BranchStorage())
  
  @get:Tag("branch-grouping")
  @get:XCollection(style = XCollection.Style.v2)
  val groupingKeyIds by stringSet(defaultGroupingKey.id)

  fun isGroupingEnabled(key: GroupingKey): Boolean {
    return groupingKeyIds.contains(key.id)
  }
}

private val defaultGroupingKey = GroupingKey.GROUPING_BY_DIRECTORY

enum class GroupingKey(val id: @NonNls String,
                       val text: @NlsActions.ActionText String? = null,
                       val description: @NlsActions.ActionDescription String? = null) {
  GROUPING_BY_DIRECTORY("directory", DvcsBundle.message("action.text.branch.group.by.directory")),
  GROUPING_BY_REPOSITORY("repository", DvcsBundle.message("action.text.branch.group.by.repository"))
}
