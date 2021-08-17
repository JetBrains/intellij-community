// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildProgressListener
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.externalSystem.model.ProjectSystemId

interface ExternalSystemOutputDispatcherFactory {
  val externalSystemId: ProjectSystemId
  fun create(buildId: Any,
             buildProgressListener: BuildProgressListener,
             appendOutputToMainConsole: Boolean,
             parsers: List<BuildOutputParser>): ExternalSystemOutputMessageDispatcher
}