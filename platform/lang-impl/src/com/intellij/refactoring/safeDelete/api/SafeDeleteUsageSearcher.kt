// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.api

import com.intellij.model.Pointer
import com.intellij.model.search.SearchParameters
import com.intellij.model.search.Searcher
import com.intellij.openapi.project.Project

class SafeDeleteSearchParameters(val target: SafeDeleteTarget, private val project: Project) : SearchParameters<SafeDeleteUsage> {
  private val pointer: Pointer<out SafeDeleteTarget> = target.createPointer()

  override fun getProject(): Project = project

  override fun areValid(): Boolean = pointer.dereference() != null
}

interface SafeDeleteUsageSearcher : Searcher<SafeDeleteSearchParameters, SafeDeleteUsage>