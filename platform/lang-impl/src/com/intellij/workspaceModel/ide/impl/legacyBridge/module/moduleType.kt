@file:JvmName("ModuleTypeUtils")
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.platform.workspace.jps.entities.ModuleTypeId

const val WEB_MODULE_ENTITY_TYPE_ID_NAME = "WEB_MODULE"

@JvmField
val WEB_MODULE_ENTITY_TYPE_ID = ModuleTypeId(WEB_MODULE_ENTITY_TYPE_ID_NAME)