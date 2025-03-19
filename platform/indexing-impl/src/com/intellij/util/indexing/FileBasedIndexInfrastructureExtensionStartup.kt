// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FileBasedIndexInfrastructureExtensionStartup : StartupActivity.RequiredForSmartMode {

  override fun runActivity(project: Project) {
    FileBasedIndexInfrastructureExtension.attachAllExtensionsData(project)
  }
}