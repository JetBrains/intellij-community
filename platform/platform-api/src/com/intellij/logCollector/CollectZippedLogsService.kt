// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logCollector

import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use 'IdeUiService.invokeLogCollectionAction' instead")
@ApiStatus.Internal
@Service(Service.Level.APP)
class CollectZippedLogsService {
  fun collectZippedLogs(project: Project?): Unit = IdeUiService.getInstance().invokeLogCollectionAction(project)
}
