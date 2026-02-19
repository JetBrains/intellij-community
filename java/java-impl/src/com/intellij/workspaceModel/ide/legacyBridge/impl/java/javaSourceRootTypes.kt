@file:JvmName("JavaSourceRootTypeUtils")
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge.impl.java

import com.intellij.platform.workspace.jps.entities.SourceRootTypeId

@JvmField
public val JAVA_SOURCE_ROOT_ENTITY_TYPE_ID: SourceRootTypeId = SourceRootTypeId("java-source")

@JvmField
public val JAVA_TEST_ROOT_ENTITY_TYPE_ID: SourceRootTypeId = SourceRootTypeId("java-test")

@JvmField
public val JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID: SourceRootTypeId = SourceRootTypeId("java-resource")

@JvmField
public val JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID: SourceRootTypeId = SourceRootTypeId("java-test-resource")