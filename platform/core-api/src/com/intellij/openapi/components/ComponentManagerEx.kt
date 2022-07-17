// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ComponentManagerEx {
  // in some cases we cannot get service by class
  /**
   * Light service is not supported.
   */
  fun <T : Any> getServiceByClassName(serviceClassName: String): T?
}