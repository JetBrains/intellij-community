// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleRepository

import com.intellij.platform.runtime.repository.IncludedRuntimeModule
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimePluginHeader
import com.intellij.platform.runtime.repository.impl.RuntimePluginHeaderImpl
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import org.assertj.core.api.Assertions.assertThat

class ExpectedRuntimeRepositoryBuilder {
  private val descriptors = ArrayList<RawRuntimeModuleDescriptor>()
  private val headers = ArrayList<RuntimePluginHeader>()

  fun descriptor(id: RuntimeModuleId, resources: List<String>, dependencies: List<RuntimeModuleId> = emptyList()) {
    descriptors.add(RawRuntimeModuleDescriptor.create(id, resources, dependencies))
  }

  fun pluginHeader(pluginId: String, pluginDescriptorModule: RuntimeModuleId, vararg includedModules: IncludedRuntimeModule) {
    headers.add(RuntimePluginHeaderImpl(pluginId, pluginDescriptorModule, includedModules.toList()))
  }

  fun checkRuntimeModuleRepository(
    buildRepositoryData: RawRuntimeModuleRepositoryData,
  ) {
    assertThat(buildRepositoryData.allModuleIds).containsExactly(*descriptors.map { it.moduleId }.toTypedArray())
    for (expectedDescriptor in descriptors) {
      assertThat(buildRepositoryData.findDescriptor(expectedDescriptor.moduleId)!!)
        .isEqualTo(expectedDescriptor)
        .describedAs("Different data for '${expectedDescriptor.moduleId.displayName}'.")
    }

    assertThat(buildRepositoryData.pluginHeaders.map { it.pluginDescriptorModuleId }).containsExactly(*headers.map { it.pluginDescriptorModuleId }.toTypedArray())
    for (expectedHeader in headers) {
      assertThat(buildRepositoryData.pluginHeaders.first { it.pluginDescriptorModuleId == expectedHeader.pluginDescriptorModuleId })
        .isEqualTo(expectedHeader)
        .describedAs("Different data for '${expectedHeader.pluginDescriptorModuleId.displayName}'.")
    }
  }

}