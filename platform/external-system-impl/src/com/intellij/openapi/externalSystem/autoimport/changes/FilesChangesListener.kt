// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.changes

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType
import java.util.*

/**
 * Describes interface for listening modifications in files or documents.
 * All listener functions can be called with write/read/none contexts.
 * Call sequence of [init], [onFileChange] and [apply] must be called on the same thread,
 * but threads may be different for different call sequences.
 */
interface FilesChangesListener : EventListener {
  fun init() {}

  fun onFileChange(path: String, modificationStamp: Long, modificationType: ExternalSystemModificationType) {}

  fun apply() {}
}