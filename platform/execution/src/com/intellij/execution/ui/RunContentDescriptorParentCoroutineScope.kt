// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.APP)
private class RunContentDescriptorParentCoroutineScope(val cs: CoroutineScope)

internal fun createRunContentDescriptorCoroutineScope(): CoroutineScope = service<RunContentDescriptorParentCoroutineScope>().cs.childScope("RunContentDescriptor")