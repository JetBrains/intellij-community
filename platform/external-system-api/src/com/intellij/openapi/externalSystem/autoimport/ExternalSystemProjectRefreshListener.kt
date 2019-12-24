// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import org.jetbrains.annotations.ApiStatus

/**
 * Project refresh listener of specific external system (gradle, maven, sbt or etc)
 * Needed to highlight bounds of project refresh on the side of a external system
 */
@ApiStatus.Experimental
interface ExternalSystemProjectRefreshListener {

  @JvmDefault
  fun beforeProjectRefresh() {}

  @JvmDefault
  fun afterProjectRefresh(status: ExternalSystemRefreshStatus) {}
}