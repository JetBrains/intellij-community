// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.ml.MLTaskApproachBuilder
import com.intellij.platform.ml.TierDescriptor
import com.intellij.platform.ml.environment.EnvironmentExtender
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val EP_NAME_TIER_DESCRIPTOR: ExtensionPointName<TierDescriptor> = ExtensionPointName.create("com.intellij.platform.ml.descriptor")

@ApiStatus.Internal
val EP_NAME_ENVIRONMENT_EXTENDER: ExtensionPointName<EnvironmentExtender<*>> = ExtensionPointName("com.intellij.platform.ml.environmentExtender")

@ApiStatus.Internal
val EP_NAME_APPROACH_BUILDER = ExtensionPointName<MLTaskApproachBuilder<*>>("com.intellij.platform.ml.impl.approach")
