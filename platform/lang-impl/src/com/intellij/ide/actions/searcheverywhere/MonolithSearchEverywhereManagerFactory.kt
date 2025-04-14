// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.actions.SearchEverywhereManagerFactory
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MonolithSearchEverywhereManagerFactory : SearchEverywhereManagerFactory {
  override fun isAvailable(): Boolean = !Registry.`is`("search.everywhere.new.enabled", false)
  override fun getManager(project: Project): SearchEverywhereManager = project.service()
}