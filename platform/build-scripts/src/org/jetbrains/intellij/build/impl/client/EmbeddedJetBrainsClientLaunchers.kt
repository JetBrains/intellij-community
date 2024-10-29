// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.client

import org.jetbrains.intellij.build.BuildContext

/**
 * Creates a copy of [originalContext] with [org.jetbrains.intellij.build.ProductProperties] changed to JetBrains Client properties.
 * This is necessary to generate launchers for JetBrains Client in a distribution of full IDE.
 */
internal suspend fun createJetBrainsClientContextForLaunchers(originalContext: BuildContext): BuildContext? {
  if (originalContext.options.enableEmbeddedJetBrainsClient) {
    val factory = originalContext.productProperties.embeddedJetBrainsClientProperties
    if (factory != null) {
      return originalContext.createCopyForProduct(factory(), originalContext.paths.projectHome, prepareForBuild = false)
    }
  }
  return null
}

internal val ADDITIONAL_EMBEDDED_CLIENT_VM_OPTIONS: List<String> = listOf(
  "-Dintellij.platform.load.app.info.from.resources=true",
)