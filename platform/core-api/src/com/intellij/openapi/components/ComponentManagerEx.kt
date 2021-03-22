// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components

import com.intellij.openapi.extensions.PluginDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * Internal use only.
 * Backward compatibility is not provided.
 */
@ApiStatus.Internal
interface ComponentManagerEx {
  // in some cases we cannot get service by class
  /**
   * Light service is not supported.
   */
  fun <T : Any> getServiceByClassName(serviceClassName: String): T?

  // backward compatibility shim for ProjectComponent and ModuleComponent
  /**
   * Only old components but not services are processed.
   */
  fun <T : Any> processInitializedComponents(aClass: Class<T>, processor: (T, PluginDescriptor) -> Unit)
}