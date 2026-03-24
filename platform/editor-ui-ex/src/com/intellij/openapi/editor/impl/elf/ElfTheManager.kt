// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.elf

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls


/**
 * Editor lock-free API
 */
interface ElfTheManager {

  fun isElfDocument(elfOrRealDocument: Document): Boolean

  fun isRealDocument(elfOrRealDocument: Document): Boolean

  fun getElfDocument(realDocument: Document): DocumentImpl?

  fun getRealDocument(elfDocument: Document): DocumentImpl?

  fun bindElfDocument(realDocument: Document, realVirtualFile: VirtualFile)

  fun isElfCommandInProgress(): Boolean

  fun executeElfCommand(
    commandProject: Project?,
    commandName: @Nls String?,
    commandGroupId: Any?,
    command: Runnable,
  )

  companion object {
    @JvmStatic
    fun getInstance(): ElfTheManager = service()
  }
}
