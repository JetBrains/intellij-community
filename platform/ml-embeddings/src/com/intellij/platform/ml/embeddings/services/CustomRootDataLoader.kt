// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.services

import ai.grazie.utils.mpp.DataLoader
import ai.grazie.utils.mpp.RootDataLoader
import ai.grazie.utils.mpp.RootStreamDataLoader
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.inputStream

class CustomRootDataLoader(private val root: Path) : RootDataLoader, RootStreamDataLoader {
  override fun stream(path: DataLoader.Path): InputStream {
    return root.resolve(path.toRelativePath()).inputStream()
  }

  override suspend fun bytes(path: DataLoader.Path): ByteArray {
    return stream(path).readBytes()
  }

  override suspend fun text(path: DataLoader.Path): String {
    return bytes(path).toString(Charsets.UTF_8)
  }
}
