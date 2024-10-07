// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.entities

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.platform.ml.embeddings.utils.splitIdentifierIntoTokens

class IndexableFile(override val id: EntityId) : IndexableEntity {
  constructor(file: VirtualFile) : this(EntityId(file.name))
  override val indexableRepresentation: String by lazy { splitIdentifierIntoTokens(FileUtilRt.getNameWithoutExtension(id.id)) }
}