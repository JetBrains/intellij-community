// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.services

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.search.indices.IndexType

/**
 * IndexPersistedEventsCounter interface represents a mechanism for sending persisted count of events to external storage.
 */
interface IndexPersistedEventsCounter {
  companion object {
    val EP_NAME = ProjectExtensionPointName<IndexPersistedEventsCounter>("com.intellij.platform.ml.embeddings.indexPersistedEventsCounter")
  }

  suspend fun sendPersistedCount(indexType: IndexType, project: Project)
}