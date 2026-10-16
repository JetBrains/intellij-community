// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.debug

import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.application

/**
 * Ensures [ImageDebugUtil] is loaded on IDE startup, because debugger renderers later resolve and invoke its static methods by FQN.
 */
internal class ImageDebugUtilLoader : ProjectActivity {
  init {
      if (application.isHeadlessEnvironment) throw ExtensionNotApplicableException.create()
  }

  override suspend fun execute(project: Project) {
    ImageDebugUtil.ensureLoaded()
  }
}
