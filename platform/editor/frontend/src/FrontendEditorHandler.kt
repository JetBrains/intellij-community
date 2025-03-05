// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.frontend

import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.editor.EditorEntity
import fleet.kernel.rete.each
import fleet.kernel.rete.launchOnEach
import kotlinx.coroutines.awaitCancellation

/**
 * Listens for the appearance of new [EditorEntity] and creates a local editor for each [EditorEntity].
 */
internal class FrontendEditorHandler : ProjectActivity {

  override suspend fun execute(project: Project) {
    if (!isRhizomeAdEnabled) return
    EditorEntity.each().launchOnEach {
      LOG.debug("editor created: $it")
      try {
        awaitCancellation()
      }
      finally {
        LOG.debug("editor released: $it")
      }
    }
  }
}
