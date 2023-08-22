// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation

private const val NEW_THIRD_PARTY_THREAD_POST_URL = "https://exa-marketplace-listener.labs.jb.gg/trackerRpc/idea/createScr"

/**
 * Submits error reports in plugins to [JetBrains Marketplace](https://plugins.jetbrains.com/).
 */
@InternalIgnoreDependencyViolation
class JetBrainsMarketplaceErrorReportSubmitter: ITNReporter(NEW_THIRD_PARTY_THREAD_POST_URL) {
  override fun getReportActionText(): String =
    DiagnosticBundle.message("error.dialog.notice.third-party.plugin.send")

  override fun getPrivacyNoticeText(): String =
    DiagnosticBundle.message("error.dialog.notice.third-party.plugin.exception")

}