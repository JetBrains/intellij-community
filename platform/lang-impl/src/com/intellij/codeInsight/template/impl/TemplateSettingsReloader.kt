// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAndBackgroundWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.fileLogger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

/**
 * Reloads [TemplateSettings] when the set of the default live templates changes.
 * Aim is to parse resources of every plugin in the background and apply them off the EDT as it might be quite heavy.
 */
@Service(Service.Level.APP)
internal class TemplateSettingsReloader(coroutineScope: CoroutineScope) {
  private val requests = Channel<Unit>(Channel.CONFLATED)

  init {
    coroutineScope.launch(CoroutineName("live template defaults reload")) {
      for (request in requests) {
        try {
          reload()
        }
        catch (e: Throwable) {
          rethrowControlFlowException(e)
          fileLogger().error("Cannot reload the default live templates", e)
        }
      }
    }
  }

  /** Parses the default live templates in the background and applies them under the background write action. */
  @VisibleForTesting
  suspend fun reload() {
    readAndBackgroundWriteAction {
      // here we wait for the write action of the plugin load before
      val parsed = TemplateSettings.parseDefaultLiveTemplates()
      writeAction {
        TemplateSettings.getInstance().reloadWithParsedDefaults(parsed)
      }
    }
  }

  /** Fire-forget. The requests of one burst reload once. */
  fun schedule() {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      // a test observes the templates right after a plugin load, so the reload stays sync
      TemplateSettings.getInstance().reloadWithParsedDefaults(TemplateSettings.parseDefaultLiveTemplates())
      return
    }
    requests.trySend(Unit)
  }
}
