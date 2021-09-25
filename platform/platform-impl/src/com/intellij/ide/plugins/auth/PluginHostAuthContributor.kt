// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.auth

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Lets plugin authors provide custom authorization headers to contributed plugin repos.
 *
 * This EP must only contribute headers to a single url.
 * Also, each custom url must have no more than a single [PluginHostAuthContributor]
 * It's also the responsibility of a particular plugin to implement an authorization flow.
 * Don't forget to call [PluginHostAuthService.authorizationChanged] if your authorization
 * is stateful and headers may change over time.
 */
interface PluginHostAuthContributor {

  /**
   * Return false if authorization isn't ready or the url shouldn't be handled
   */
  fun canHandle(url: String): Boolean

  fun getCustomHeaders(): Map<String, String>

  companion object {
    val EP_NAME = ExtensionPointName.create<PluginHostAuthContributor>("com.intellij.pluginHostAuthContributor")
  }
}