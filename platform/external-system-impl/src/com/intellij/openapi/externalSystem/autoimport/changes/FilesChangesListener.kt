// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.changes

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.Stamp
import org.jetbrains.annotations.ApiStatus

/**
 * Describes interface for listening modifications in files or documents.
 * All listener functions can be called with write/read/none contexts.
 * Call sequence of [init], [onFileChange] and [apply] must be called on the same thread,
 * but threads may be different for different call sequences.
 */
@ApiStatus.Internal
interface FilesChangesListener {

  fun init() {}

  fun onFileChange(stamp: Stamp, path: String, modificationStamp: Long, modificationType: ExternalSystemModificationType) {}

  fun apply() {}
}