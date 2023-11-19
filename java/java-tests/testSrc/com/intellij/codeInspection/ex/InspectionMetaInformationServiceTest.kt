// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.*
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Paths
import kotlin.test.assertEquals

class InspectionMetaInformationServiceTest {

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val tempDirManager = TemporaryDirectory()

  @Rule
  @JvmField
  val initInspectionRule = InitInspectionRule()

  @Rule
  @JvmField
  val invalidateMetaInformationServiceRule = InvalidateMetaInformationServiceRule()

  private fun doTest(task: suspend (Project) -> Unit) {
    runBlocking {
      loadAndUseProjectInLoadComponentStateMode(tempDirManager, { Paths.get(it.path) }, task)
    }
  }

  @Test
  fun testCWEids() {
    doTest {
      val state = service<InspectionMetaInformationService>().getState()
      assertEquals(listOf(570, 571), state.inspections["ConstantValue"]?.cweIds)
      assertEquals(listOf(476, 754), state.inspections["NullableProblems"]?.cweIds)
    }
  }

  class InvalidateMetaInformationServiceRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = statement {
      try {
        base.evaluate()
      } finally {
        service<InspectionMetaInformationService>().invalidateState()
      }
    }
  }

}