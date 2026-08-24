// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.tools.ide.metrics.benchmark.Benchmark

@PerformanceUnitTest
class JsonFormattingPerformanceTest : JsonTestCase() {
  override fun getTestDataPath(): String {
    return super.getTestDataPath() + "/formatting"
  }

  fun testHugeJsonFilePerformance() {
    // IDEA-195340 bad JSON kills IntelliJ
    myFixture.configureByFile(getTestName(false) + ".json")
    Benchmark.newBenchmark(getTestName(false)) {
      reformatAndCheck()
    }.attempts(1).start()
  }

  private fun reformatAndCheck() {
    WriteCommandAction.runWriteCommandAction(project) {
      val codeStyleManager = CodeStyleManager.getInstance(myFixture.project)
      codeStyleManager.reformat(myFixture.file)
    }
    myFixture.checkResultByFile(getTestName(false) + "_after.json")
  }
}
