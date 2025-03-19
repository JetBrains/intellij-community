// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.chain.AbstractDeclarativeCallChainCustomSettingsProvider

class JavaMethodChainsHintsCustomSettingsProvider : AbstractDeclarativeCallChainCustomSettingsProvider(defaultChainLength = 2)