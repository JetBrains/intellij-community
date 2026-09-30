// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
@ApiStatus.Internal
interface DocumentPatchVersionAccessor {
  fun getDocumentVersion(document: Document, project: Project): DocumentPatchVersion?

  companion object {
    internal val EP_NAME = ExtensionPointName<DocumentPatchVersionAccessor>("com.intellij.rpc.documentVersionAccessor")

    /**
     * Gives access to the document patch version shared between the frontend and the backend.
     *
     * In monolith mode, always returns null.
     * In split mode, returns the version of the document that can be used for comparison both on the frontend and the backend.
     *
     * Code outside the platform uses [patchVersion] instead.
     */
    fun getDocumentVersion(document: Document, project: Project): DocumentPatchVersion? {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.getDocumentVersion(document, project) }
    }
  }
}
