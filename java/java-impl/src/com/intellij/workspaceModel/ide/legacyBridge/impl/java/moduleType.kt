@file:JvmName("JavaModuleTypeUtils")
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge.impl.java

import com.intellij.platform.workspace.jps.entities.ModuleTypeId

const val JAVA_MODULE_ENTITY_TYPE_ID_NAME = "JAVA_MODULE"

@JvmField
val JAVA_MODULE_ENTITY_TYPE_ID = ModuleTypeId(JAVA_MODULE_ENTITY_TYPE_ID_NAME)