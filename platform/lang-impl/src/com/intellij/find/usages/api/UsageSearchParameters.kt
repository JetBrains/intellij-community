// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.usages.api

import com.intellij.model.search.SearchParameters
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope

interface UsageSearchParameters : SearchParameters<Usage> {

  val target: SearchTarget

  val searchScope: SearchScope

  operator fun component1(): Project = project

  operator fun component2(): SearchTarget = target

  operator fun component3(): SearchScope = searchScope
}
