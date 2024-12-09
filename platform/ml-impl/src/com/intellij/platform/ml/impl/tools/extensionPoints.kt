// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.tools

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.ml.FeatureProvider

internal val EP_NAME_FEATURE_PROVIDER: ExtensionPointName<FeatureProvider> = ExtensionPointName.create("com.intellij.platform.ml.featureProvider")
