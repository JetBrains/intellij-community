// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework.utils.module

import com.intellij.java.workspace.entities.javaResourceRoots
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import java.util.function.BiConsumer
import java.util.function.Consumer

class ExternalSystemSourceRootAssertion<T> private constructor(
  private val assertSourceRoot: (ExternalSystemSourceType, List<T>) -> Unit,
) {

  private val checkedRoots = HashSet<ExternalSystemSourceType>()

  fun sourceRoots(type: ExternalSystemSourceType, vararg expectedRoots: T): ExternalSystemSourceRootAssertion<T> {
    require(checkedRoots.add(type)) {
      "The source root of type $type is already checked"
    }
    assertSourceRoot(type, expectedRoots.toList())
    return this
  }

  companion object {

    @JvmStatic
    fun <T> assertSourceRoots(
      applyAssertion: Consumer<ExternalSystemSourceRootAssertion<T>>,
      assertSourceRoots: BiConsumer<ExternalSystemSourceType, List<T>>,
    ) {
      val assertion = ExternalSystemSourceRootAssertion(assertSourceRoots::accept)
      applyAssertion.accept(assertion)
      for (type in ExternalSystemSourceType.entries) {
        if (type !in assertion.checkedRoots) {
          assertSourceRoots.accept(type, emptyList())
        }
      }
    }

    @JvmStatic
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
  }
}