@file:JvmName("ModuleTypeUtils")
@file:ApiStatus.Internal

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.platform.workspace.jps.entities.ModuleTypeId
import org.jetbrains.annotations.ApiStatus

const val WEB_MODULE_ENTITY_TYPE_ID_NAME: String = "WEB_MODULE"

@JvmField
val WEB_MODULE_ENTITY_TYPE_ID: ModuleTypeId = ModuleTypeId(WEB_MODULE_ENTITY_TYPE_ID_NAME)