// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildStepListener
import org.jetbrains.intellij.build.logging.TeamCityBuildMessageLogger.Companion.SpanAwareServiceMessage
import org.junit.jupiter.api.TestInfo

/**
 * See [BuildStepListener].
 *
 * Reports each build step launched with [org.jetbrains.intellij.build.executeStep] as a TeamCity test:
 * * such a test must be started in its own dedicated [flow](https://www.jetbrains.com/help/teamcity/service-messages.html#Message+FlowId), see [org.jetbrains.intellij.build.executeStep]
 * * each flow started withing a test should be a transitive subflow of that test's own flow, see [org.jetbrains.intellij.build.logging.TeamCityBuildMessageLogger.withFlow]
 *
 * Otherwise, tests will not be correctly displayed in a TeamCity build log.
 */
@ApiStatus.Internal
open class BuildStepTeamCityListener(testInfo: TestInfo) : BuildStepListener() {
  private val testName: String = "${testInfo.testClass.get().canonicalName}.${testInfo.testMethod.orElseThrow().name}"

  private fun reportTestEvent(testEvent: String, stepId: String, vararg attributes: Pair<String, String>) {
    println(SpanAwareServiceMessage(testEvent, "name" to "$testName($stepId)", *attributes))
  }

  override suspend fun onStart(stepId: String, messages: BuildMessages) {
    super.onStart(stepId, messages)
    reportTestEvent(ServiceMessageTypes.TEST_STARTED, stepId)
    messages.warning("This test is automatically generated from the build step '$stepId'")
    messages.warning("To run it locally, please invoke '$testName'")
  }

  override suspend fun onSkipping(stepId: String, messages: BuildMessages) {
    super.onSkipping(stepId, messages)
    reportTestEvent(ServiceMessageTypes.TEST_IGNORED, stepId)
  }

  override suspend fun onFailure(stepId: String, failure: Throwable, messages: BuildMessages) {
    // no need to throw the build step failure, just reporting it to TeamCity as a test failure,
    // providing an option to mute it without muting the main test
    reportTestEvent(ServiceMessageTypes.TEST_FAILED, stepId, "message" to "${failure.message}", "details" to failure.stackTraceToString())
  }

  override suspend fun onCompletion(stepId: String, messages: BuildMessages) {
    super.onCompletion(stepId, messages)
    reportTestEvent(ServiceMessageTypes.TEST_FINISHED, stepId)
  }
}