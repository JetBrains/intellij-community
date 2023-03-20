// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.api

import com.intellij.model.search.SearchParameters
import com.intellij.model.search.Searcher
import com.intellij.openapi.project.Project

class SafeDeleteAdditionalTargetsSearchParameters(val rootTarget : SafeDeleteTarget, private val project: Project) : SearchParameters<SafeDeleteTarget> {
  private val targetPointer = rootTarget.createPointer()
  override fun getProject(): Project = project

  override fun areValid(): Boolean = targetPointer.dereference() != null
}

interface SafeDeleteAdditionalTargetSearcher : Searcher<SafeDeleteAdditionalTargetsSearchParameters, SafeDeleteTarget>

