// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.StatusItemMetadata
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TrafficLightStatusItemMetadata(val count: Int, val severity: HighlightSeverity) : StatusItemMetadata