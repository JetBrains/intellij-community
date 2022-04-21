// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.projector

import org.jetbrains.intellij.build.ProductProperties

private const val projectorPlugin = "intellij.cwm.plugin.projector"
private const val projectorJar = "plugins/cwm-plugin-projector/lib/projector/projector.jar"

fun configure(productProperties: ProductProperties) {
  if (productProperties.productLayout.bundledPluginModules.contains(projectorPlugin) &&
      productProperties.versionCheckerConfig?.containsKey(projectorJar) == false) {
    (productProperties.versionCheckerConfig as? MutableMap)?.put(projectorJar, "17")
  }
}