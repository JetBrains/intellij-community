// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

private class SharedDebuggerInitializationProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    SharedJavaDebuggerManager.getInstance(project)
  }
}
