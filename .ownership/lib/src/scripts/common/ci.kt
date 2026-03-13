package com.intellij.codeowners.scripts.common

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.TestFailed
import jetbrains.buildServer.messages.serviceMessages.TestFinished
import jetbrains.buildServer.messages.serviceMessages.TestStarted

fun <R> runReportingErrorsAsTests(testName: String, block: () -> R): R = CiTestReporter(testName).run {
  try {
    onStart()
    val result = block()
    onSuccess()
    result
  }
  catch (e: Throwable) {
    onFailure(e)
    throw e
  }
}

private class CiTestReporter(val testName: String) {
  companion object {
    private val isUnderTeamCity = !System.getenv("TEAMCITY_VERSION").isNullOrBlank()
  }

  private var startedAt: Long? = null
  private var finished: Boolean = false

  fun onStart() {
    check(startedAt == null) { "Test $testName already started" }
    startedAt = System.currentTimeMillis()
    TestStarted(testName, false, null).emit()
  }

  fun onSuccess() {
    onFinish()
  }

  fun onFailure(throwable: Throwable) {
    onFinish(throwable = throwable)
  }

  private fun onFinish(throwable: Throwable? = null) {
    check(!finished) { "Test $testName already finished" }
    finished = true

    val startedAt = checkNotNull(startedAt) { "Test $testName not started" }
    val finishedAt = System.currentTimeMillis()
    val duration = (finishedAt - startedAt).toInt()

    if (throwable != null) {
      TestFailed(testName, throwable).emit()
    }
    TestFinished(testName, duration).emit()
  }

  private fun ServiceMessage.emit() {
    if (!isUnderTeamCity) return
    println(this)
  }
}