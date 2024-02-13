// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.openapi.util.registry.Registry

public fun useQueryCacheWorkspaceModelApi(): Boolean {
  return Registry.`is`("ide.workspace.model.use.query.cache.api")
}

public fun useReactiveWorkspaceModelApi(): Boolean {
  return Registry.`is`("ide.workspace.model.use.reactive.api")
}

public fun useNewWorkspaceModelApiForUnloadedModules(): Boolean {
  return Registry.`is`("ide.workspace.model.use.new.api.unloaded.modules", false)
}
