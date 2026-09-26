package com.intellij.database

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

@Service
class GridScopeProvider(val cs: CoroutineScope)
