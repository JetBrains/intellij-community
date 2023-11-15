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
  val initServiceRule = InitMetaInformationServiceRule()

  private fun doTest(task: suspend (Project) -> Unit) {
    runBlocking {
      loadAndUseProjectInLoadComponentStateMode(tempDirManager, { Paths.get(it.path) }, task)
    }
  }

  @Test
  fun testCWEids() {
    doTest {
      assertEquals(listOf(570, 571), service<InspectionMetaInformationService>().getMetaInformation("ConstantValue")?.cweIds)
      assertEquals(listOf(476, 754), service<InspectionMetaInformationService>().getMetaInformation("NullableProblems")?.cweIds)
    }
  }

  class InitMetaInformationServiceRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = statement {
      try {
        runBlocking {
          service<InspectionMetaInformationService>().initialize().await()
        }
        base.evaluate()
      } finally {
        service<InspectionMetaInformationService>().dropStorage()
      }
    }
  }

}