// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.services

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.search.indices.EntitySourceType
import java.util.concurrent.atomic.AtomicLong

interface IndexPersistedEventsCounter {
  companion object {
    val EP_NAME = ProjectExtensionPointName<IndexPersistedEventsCounter>("com.intellij.platform.ml.embeddings.indexPersistedEventsCounter")
  }

  suspend fun sendPersistedCount(project: Project, countMap: Map<EntitySourceType, AtomicLong>)
}