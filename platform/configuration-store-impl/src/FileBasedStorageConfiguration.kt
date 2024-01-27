// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

interface FileBasedStorageConfiguration {
  val isUseVfsForWrite: Boolean
}

internal val defaultFileBasedStorageConfiguration = object : FileBasedStorageConfiguration {
  override val isUseVfsForWrite: Boolean
    get() = true
}
