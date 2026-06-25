// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.ide.util.gotoByName.LanguageRef
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<SearchEverywhereWarmupActivity>()
private val warmedUp = AtomicBoolean(false)

/**
 * Warms up the Search Everywhere class graph on a background thread shortly after a project is opened, so that the first
 * SE popup open does not pay the cold class-loading cost on the EDT.
 */
internal class SearchEverywhereWarmupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) return
    if (!warmedUp.compareAndSet(false, true)) return

    runCatching { SearchEverywhereContributor.EP_NAME.extensionList }
      .onFailure { LOG.debug("SE warmup: failed to preload contributor factories", it) }
    runCatching { SearchEverywhereMlContributorReplacement.getFirstExtension() }
      .onFailure { LOG.debug("SE warmup: failed to preload ML contributor replacement", it) }

    // Eagerly compute the file-type and language filter icons off the EDT.
    runCatching { FileTypeRef.forAllFileTypes().forEach { it.icon } }
      .onFailure { LOG.debug("SE warmup: failed to preload file type icons", it) }
    runCatching { LanguageRef.forAllLanguages().forEach { it.icon } }
      .onFailure { LOG.debug("SE warmup: failed to preload language icons", it) }
  }
}
