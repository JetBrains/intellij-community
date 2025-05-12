// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ModuleStoreFactory {
  fun createModuleStore(module: Module): IComponentStore
}