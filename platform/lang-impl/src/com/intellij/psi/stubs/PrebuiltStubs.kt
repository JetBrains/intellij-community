// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs

import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.IndexInfrastructure
import java.nio.file.Path

abstract class PlatformPrebuiltStubsProviderBase: PrebuiltStubsProviderBase() {
  override fun getIndexRoot(): Path = IndexInfrastructure.getPersistentIndexRoot().resolve("prebuilt/$dirName")

  override fun getIndexVersion(): Int = FileBasedIndexExtension.EXTENSION_POINT_NAME.findExtension(StubUpdatingIndex::class.java)!!.version
}
