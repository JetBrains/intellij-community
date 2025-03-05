// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

/**
 * @author Vitaliy.Bibaev
 */
@Service(Service.Level.PROJECT)
@State(name = "ProjectInlineFactors", storages = [Storage(StoragePathMacros.CACHE_FILE)], reportStatistic = false)
class ProjectUserFactorStorage : UserFactorStorageBase()