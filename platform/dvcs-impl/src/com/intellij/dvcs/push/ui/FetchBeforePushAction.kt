// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui

import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.project.Project

/**
 * Fetches the remote commits that are not fetched, so that the push dialog can count the commits that a push replaces.
 * The dialog shows the action only when a push fails without the fetch.
 */
internal class FetchBeforePushAction : PushActionBase() {
  override fun isEnabled(ui: VcsPushUi): Boolean = ui.canPush() && ui.isFetchRequired

  override fun isVisible(ui: VcsPushUi): Boolean = ui.isFetchRequired

  override fun getDescription(ui: VcsPushUi, enabled: Boolean): String = DvcsBundle.message("action.fetch.before.push.description")

  override fun actionPerformed(project: Project, ui: VcsPushUi) {
    ui.fetchRemoteCommits()
  }
}
