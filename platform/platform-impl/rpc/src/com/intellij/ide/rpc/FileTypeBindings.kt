// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

/**
 * Mark given frontend's [Document], so [Editor]s created for this [Document] should be bind with backend.
 *
 * This method is useful when you want all the editors created for this [Document] to have backend features.
 */
// TODO: it is not ok to attach Document to fileType
@ApiStatus.Internal
internal fun Document.bindEditorsToBackend() {
  this.putUserData(BIND_DOCUMENT_EDITORS, true)
}

/**
 * @return true if the [Editor] should be bind with the backend editor, false otherwise
 */
@ApiStatus.Internal
fun Editor.shouldBindToBackend(): Boolean {
  return document.getUserData(BIND_DOCUMENT_EDITORS) == true
}

@ApiStatus.Internal
private val BIND_DOCUMENT_EDITORS: Key<Boolean> = Key<Boolean>("bindDocumentEditors")