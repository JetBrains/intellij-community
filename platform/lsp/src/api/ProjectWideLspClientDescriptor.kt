// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe

/**
 * An [LspClientDescriptor] that assumes that a single LSP server is going to serve the whole project, regardless of the project structure.
 * So, it uses all [BaseProjectDirectories.getBaseDirectories] as LSP server roots.
 */
abstract class ProjectWideLspClientDescriptor(project: Project, @NlsSafe presentableName: String) :
  LspClientDescriptor(project, presentableName, *project.getBaseDirectories().toTypedArray())
