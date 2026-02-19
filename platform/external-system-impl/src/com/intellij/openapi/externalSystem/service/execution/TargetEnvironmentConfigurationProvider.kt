// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.util.PathMapper
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface TargetEnvironmentConfigurationProvider {
  val environmentConfiguration: TargetEnvironmentConfiguration
  val pathMapper: PathMapper?
}