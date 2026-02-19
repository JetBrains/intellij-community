// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.rhizomedb.EntityType
import org.jetbrains.annotations.ApiStatus.Internal

interface EntityTypeProvider {

  fun entityTypes(): List<EntityType<*>>

  @Internal
  companion object {
    val EP_NAME: ExtensionPointName<EntityTypeProvider> = ExtensionPointName.create("com.intellij.platform.entityTypes")
  }
}
