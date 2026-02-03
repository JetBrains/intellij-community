// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ExternalSystemSourceRootAssertions")

package com.intellij.platform.externalSystem.testFramework.utils.module

import com.intellij.java.workspace.entities.javaResourceRoots
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.platform.testFramework.assertion.moduleAssertion.SourceRootAssertions.assertSourceRoots
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import java.nio.file.Path

fun assertNoSourceRoots(
  module: Module,
) {
  assertSourceRoots(module) {}
}

fun assertSourceRoots(
  module: Module,
  configure: ExternalSystemSourceRootAssertion<Path>.() -> Unit,
) {
  ExternalSystemSourceRootAssertion.assertSourceRoots(configure) { type, expectedRoots ->
    assertSourceRoots(module, { it.exType == type }, expectedRoots) {
      "${module.name} source root of type $type"
    }
  }
}

fun assertNoSourceRoots(
  project: Project,
  moduleName: String,
) {
  assertSourceRoots(project, moduleName) {}
}

fun assertSourceRoots(
  project: Project,
  moduleName: String,
  configure: ExternalSystemSourceRootAssertion<Path>.() -> Unit,
) {
  ExternalSystemSourceRootAssertion.assertSourceRoots(configure) { type, expectedRoots ->
    assertSourceRoots(project, moduleName, { it.exType == type }, expectedRoots) {
      "$moduleName source root of type $type"
    }
  }
}

fun assertNoSourceRoots(
  virtualFileUrlManager: VirtualFileUrlManager,
  storage: EntityStorage,
  moduleName: String,
) {
  assertSourceRoots(virtualFileUrlManager, storage, moduleName) {}
}

fun assertSourceRoots(
  virtualFileUrlManager: VirtualFileUrlManager,
  storage: EntityStorage,
  moduleName: String,
  configure: ExternalSystemSourceRootAssertion<Path>.() -> Unit,
) {
  ExternalSystemSourceRootAssertion.assertSourceRoots(configure) { type, expectedRoots ->
    assertSourceRoots(virtualFileUrlManager, storage, moduleName, { it.exType == type }, expectedRoots) {
      "$moduleName source root of type $type"
    }
  }
}

fun assertNoSourceRoots(
  virtualFileUrlManager: VirtualFileUrlManager,
  moduleEntity: ModuleEntity,
) {
  assertSourceRoots(virtualFileUrlManager, moduleEntity) {}
}

fun assertSourceRoots(
  virtualFileUrlManager: VirtualFileUrlManager,
  moduleEntity: ModuleEntity,
  configure: ExternalSystemSourceRootAssertion<Path>.() -> Unit,
) {
  ExternalSystemSourceRootAssertion.assertSourceRoots(configure) { type, expectedRoots ->
    assertSourceRoots(virtualFileUrlManager, moduleEntity, { it.exType == type }, expectedRoots) {
      "${moduleEntity.name} source root of type $type"
    }
  }
}

fun assertNoSourceRoots(
  moduleEntity: ModuleEntity,
) {
  assertSourceRoots(moduleEntity) {}
}

fun assertSourceRoots(
  moduleEntity: ModuleEntity,
  configure: ExternalSystemSourceRootAssertion<VirtualFileUrl>.() -> Unit,
) {
  ExternalSystemSourceRootAssertion.assertSourceRoots(configure) { type, expectedRoots ->
    assertSourceRoots(moduleEntity, { it.exType == type }, expectedRoots) {
      "${moduleEntity.name} source root of type $type"
    }
  }
}

val SourceRootEntity.exType: ExternalSystemSourceType
  get() = when (rootTypeId) {
    JAVA_SOURCE_ROOT_ENTITY_TYPE_ID -> when (javaSourceRoots.any { it.generated }) {
      true -> ExternalSystemSourceType.SOURCE_GENERATED
      else -> ExternalSystemSourceType.SOURCE
    }
    JAVA_TEST_ROOT_ENTITY_TYPE_ID -> when (javaSourceRoots.any { it.generated }) {
      true -> ExternalSystemSourceType.TEST_GENERATED
      else -> ExternalSystemSourceType.TEST
    }
    JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID -> when (javaResourceRoots.any { it.generated }) {
      true -> ExternalSystemSourceType.RESOURCE_GENERATED
      else -> ExternalSystemSourceType.RESOURCE
    }
    JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID -> when (javaResourceRoots.any { it.generated }) {
      true -> ExternalSystemSourceType.TEST_RESOURCE_GENERATED
      else -> ExternalSystemSourceType.TEST_RESOURCE
    }
    else -> throw NoWhenBranchMatchedException("Unexpected source type: $this")
  }
