// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.api

import com.intellij.model.search.SearchParameters
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope

interface UsageSearchParameters : SearchParameters<Usage> {

  val target: SearchTarget

  val searchScope: SearchScope

  @JvmDefault
  operator fun component1(): Project = project

  @JvmDefault
  operator fun component2(): SearchTarget = target

  @JvmDefault
  operator fun component3(): SearchScope = searchScope
}
