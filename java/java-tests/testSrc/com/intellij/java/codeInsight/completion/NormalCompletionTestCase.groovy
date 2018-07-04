// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
/**
 * @author peter
 */
abstract class NormalCompletionTestCase extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal/"
  }

  def configure() {
    configureByTestName()
  }

  def checkResult() {
    checkResultByFile(getTestName(false) + "_after.java")
  }

  void doTest(String finishChar) {
    configure()
    type finishChar
    checkResult()
  }

  void doTest() throws Exception {
    configure()
    checkResult()
  }

}
