// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.index

import com.intellij.util.indexing.IndexInfrastructure
import java.nio.file.Path

abstract class PrebuiltIndexProviderBase<Value> : PrebuiltIndexProvider<Value>() {
  override fun getIndexRoot(): Path = IndexInfrastructure.getPersistentIndexRoot().resolve("prebuilt/$dirName")
}
