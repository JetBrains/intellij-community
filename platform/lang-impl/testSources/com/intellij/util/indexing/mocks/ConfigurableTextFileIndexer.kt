// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.mocks

import com.intellij.openapi.fileTypes.PlainTextFileType

open class ConfigurableTextFileIndexer(private val dependsOnContent: Boolean)
  : ConfigurableFiletypeSpecificFileIndexer(PlainTextFileType.INSTANCE) {
  override fun dependsOnFileContent(): Boolean = dependsOnContent
}
