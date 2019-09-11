// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

interface ExternalSystemProjectRefreshListener {

  /**
   * Triggers before project refresh
   */
  fun beforeProjectRefresh() {}

  /**
   * Triggers after project refresh
   */
  fun afterProjectRefresh(status: ExternalSystemRefreshStatus) {}
}