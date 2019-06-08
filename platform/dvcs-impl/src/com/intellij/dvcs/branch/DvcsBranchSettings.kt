// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.branch

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Tag

class DvcsBranchSettings : BaseState() {
  @get:Tag("favorite-branches")
  var favorites by property(BranchStorage())
  @get:Tag("excluded-from-favorite")
  var excludedFavorites by property(BranchStorage())
}