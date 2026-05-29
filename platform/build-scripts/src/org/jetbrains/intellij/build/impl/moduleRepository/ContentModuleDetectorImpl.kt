// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleRepository

import org.jetbrains.jps.model.module.JpsModule

/**
 * Provide information about [JpsModule] registered as content modules using data [org.jetbrains.intellij.build.impl.DescriptorCacheContainer].
 */
internal class ContentModuleDetectorImpl(pluginDescriptorsData: List<PluginDescriptorDataForHeader>) : ContentModuleDetector {
  private val contentModules = pluginDescriptorsData.asSequence().flatMap { it.contentModules.values }.associateBy { it.name }

  override fun findContentModuleData(jpsModule: JpsModule): ContentModuleRegistrationDataForHeader? {
    return contentModules[jpsModule.name]
  }

  override fun findContentModuleDataForTests(jpsModule: JpsModule): ContentModuleRegistrationDataForHeader? {
    val data = contentModules["${jpsModule.name}._test"]
    if (data != null) return data
    if (hasTestSourcesAndNoProductionSources(jpsModule)) {
      //some modules (e.g. intellij.rider.test.cases.common) don't use `._test` suffix
      return contentModules[jpsModule.name]
    }
    return null
  }
}
