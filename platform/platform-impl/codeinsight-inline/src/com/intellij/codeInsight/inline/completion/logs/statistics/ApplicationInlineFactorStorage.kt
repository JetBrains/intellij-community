// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service
@State(name = "ApplicationInlineFactors", storages = [(Storage(value = "inline.factors.xml", roamingType = RoamingType.DISABLED))], reportStatistic = false)
class ApplicationInlineFactorStorage : UserFactorStorageBase()


