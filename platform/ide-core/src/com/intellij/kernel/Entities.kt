// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kernel

import com.jetbrains.rhizomedb.CascadeDeleteBy
import com.jetbrains.rhizomedb.Unique
import fleet.kernel.SharedEntity
import kotlinx.serialization.json.JsonElement

interface ViewModelEntity : SharedEntity {
  @Unique
  var modelId: String
}

interface ModelPropertyEntity : SharedEntity {
  @Unique
  var id: String
  @CascadeDeleteBy
  var viewModelEntity: ViewModelEntity
  var value: JsonElement
}