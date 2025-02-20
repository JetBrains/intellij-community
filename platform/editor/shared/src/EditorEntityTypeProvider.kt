// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor

import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.editor.impl.ad.AdDocumentEntity
import com.intellij.platform.pasta.common.DocumentEntity
import com.intellij.platform.pasta.common.EditLogEntity
import com.intellij.platform.kernel.EntityTypeProvider
import com.jetbrains.rhizomedb.EntityType

internal class EditorEntityTypeProvider : EntityTypeProvider {

  override fun entityTypes(): List<EntityType<*>> {
    if (!isRhizomeAdEnabled) return emptyList()
    return listOf(
      AdDocumentEntity,
      EditorEntity,
      DocumentEntity,
      EditLogEntity,
    )
  }
}
