// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleScopeProvider
import com.intellij.openapi.module.impl.ModuleScopeProviderFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class ModuleScopeProviderFactoryImpl : ModuleScopeProviderFactory {
  override fun createProvider(module: Module): ModuleScopeProvider {
    return ModuleScopeProviderImpl(module)
  }
}