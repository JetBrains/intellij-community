// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.json

import org.jetbrains.annotations.ApiStatus
import java.io.InputStream

@ApiStatus.Experimental
interface JsonDataSerializer {
  fun toJsonBytes(content: Any): ByteArray
}