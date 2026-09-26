// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModulePackageIndex
import com.intellij.openapi.roots.ModulePackageIndexFactory

internal class ModulePackageIndexFactoryImpl: ModulePackageIndexFactory {
  override fun getService(module: Module): ModulePackageIndex {
    return ModulePackageIndexImpl(module)
  }
}