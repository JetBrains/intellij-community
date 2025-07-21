@file:JvmName("JavaModuleTypeUtils")
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge.impl.java

import com.intellij.platform.workspace.jps.entities.ModuleTypeId

public const val JAVA_MODULE_ENTITY_TYPE_ID_NAME: String = "JAVA_MODULE"

@JvmField
public val JAVA_MODULE_ENTITY_TYPE_ID: ModuleTypeId = ModuleTypeId(JAVA_MODULE_ENTITY_TYPE_ID_NAME)
