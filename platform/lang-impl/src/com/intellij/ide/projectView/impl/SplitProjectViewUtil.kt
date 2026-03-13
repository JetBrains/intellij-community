@file:ApiStatus.Internal
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

fun isProjectViewSplit(): Boolean = Registry.`is`("project.view.toolwindow.split", defaultValue = false)
