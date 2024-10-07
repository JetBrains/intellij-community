// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.jvm.indices

import com.fasterxml.jackson.annotation.JsonValue

enum class EntitySourceType {
  DEFAULT,
  EXTERNAL;

  @JsonValue
  fun value(): Int = ordinal
}