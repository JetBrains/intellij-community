// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * This is a temporary workaround.
 * This API will be removed in 2026.3 - IDEA-389819.
 */
@Internal
interface ExternalSystemAutoImportAwareListener {

  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<ExternalSystemAutoImportAwareListener> = Topic(
      ExternalSystemAutoImportAwareListener::class.java,
      Topic.BroadcastDirection.TO_DIRECT_CHILDREN
    )
  }

  fun autoImportAwareOperationStarted()

  fun autoImportAwareOperationCompleted()
}