// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.tests

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.platform.lsp.testFramework.checkLspHighlighting as checkLspHighlightingInTestFramework
import com.intellij.platform.lsp.testFramework.checkLspHighlightingForData as checkLspHighlightingForDataInTestFramework
import com.intellij.platform.lsp.testFramework.waitForDiagnosticsFromLspServer as waitForDiagnosticsFromLspServerInTestFramework
import com.intellij.platform.lsp.testFramework.waitUntilFileOpenedByLspServer as waitUntilFileOpenedByLspServerInTestFramework

@Deprecated(
  "Moved to com.intellij.platform.lsp.testFramework",
  ReplaceWith(
    "waitUntilFileOpenedByLspServer(project, file)",
    "com.intellij.platform.lsp.testFramework.waitUntilFileOpenedByLspServer",
  ),
)
@RequiresBlockingContext
@RequiresEdt
fun waitUntilFileOpenedByLspServer(project: Project, file: VirtualFile): Unit =
  waitUntilFileOpenedByLspServerInTestFramework(project, file)

@Deprecated(
  "Moved to com.intellij.platform.lsp.testFramework",
  ReplaceWith(
    "waitForDiagnosticsFromLspServer(project, file, timeout)",
    "com.intellij.platform.lsp.testFramework.waitForDiagnosticsFromLspServer",
  ),
)
@RequiresBlockingContext
@JvmOverloads
@RequiresEdt
fun waitForDiagnosticsFromLspServer(project: Project, file: VirtualFile, timeout: Int = 30): Unit =
  waitForDiagnosticsFromLspServerInTestFramework(project, file, timeout)

@Deprecated(
  "Moved to com.intellij.platform.lsp.testFramework",
  ReplaceWith(
    "checkLspHighlighting()",
    "com.intellij.platform.lsp.testFramework.checkLspHighlighting",
  ),
)
@RequiresBlockingContext
@RequiresEdt
fun CodeInsightTestFixture.checkLspHighlighting(): Unit =
  checkLspHighlightingInTestFramework()

@Deprecated(
  "Moved to com.intellij.platform.lsp.testFramework",
  ReplaceWith(
    "checkLspHighlightingForData(data)",
    "com.intellij.platform.lsp.testFramework.checkLspHighlightingForData",
  ),
)
@RequiresBlockingContext
@RequiresEdt
fun CodeInsightTestFixture.checkLspHighlightingForData(data: ExpectedHighlightingData): Unit =
  checkLspHighlightingForDataInTestFramework(data)