// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.settings


interface EmbeddingIndexSettings {
  val shouldIndexActions: Boolean
    get() = false
  val shouldIndexFiles: Boolean
    get() = false
  val shouldIndexClasses: Boolean
    get() = false
  val shouldIndexSymbols: Boolean
    get() = false
}