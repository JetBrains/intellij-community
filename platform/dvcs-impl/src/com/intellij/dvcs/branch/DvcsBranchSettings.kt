// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.branch

import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.util.NlsActions
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.NonNls

class DvcsBranchSettings : BaseState() {
  @get:Tag("favorite-branches")
  var favorites by property(BranchStorage())
  @get:Tag("excluded-from-favorite")
  var excludedFavorites by property(BranchStorage())
  @get:Tag("branch-grouping")
  @get:XCollection(style = XCollection.Style.v2)
  val groupingKeyIds by stringSet(defaultGroupingKey.id)
}

private val defaultGroupingKey = GroupingKey.GROUPING_BY_DIRECTORY

enum class GroupingKey(val id: @NonNls String,
                       val text: @NlsActions.ActionText String? = null,
                       val description: @NlsActions.ActionDescription String? = null) {
  GROUPING_BY_DIRECTORY("directory", DvcsBundle.message("action.text.branch.group.by.directory")),
  GROUPING_BY_REPOSITORY("repository", DvcsBundle.message("action.text.branch.group.by.repository"))
}
