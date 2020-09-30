// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.api

import com.intellij.model.search.SearchParameters
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope

interface RenameUsageSearchParameters : SearchParameters<RenameUsage> {

  val target: RenameTarget

  val searchScope: SearchScope

  @JvmDefault
  operator fun component1(): Project = project

  @JvmDefault
  operator fun component2(): RenameTarget = target

  @JvmDefault
  operator fun component3(): SearchScope = searchScope
}
