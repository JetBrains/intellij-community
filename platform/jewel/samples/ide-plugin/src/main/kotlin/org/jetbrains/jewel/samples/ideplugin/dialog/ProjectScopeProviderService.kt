// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.ideplugin.dialog

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT) internal class ProjectScopeProviderService(val scope: CoroutineScope)
