// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.LogActionsDuringTest
import com.intellij.testGuiFramework.impl.ScreenshotsDuringTest
import com.intellij.testGuiFramework.util.logEndTest
import com.intellij.testGuiFramework.util.logStartTest
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ErrorCollector
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName

open class KotlinGuiTestCase : GuiTestCase() {

  @Rule
  @JvmField
  val testMethod = TestName()

  @Rule
  @JvmField
  val screenshotsDuringTest = ScreenshotsDuringTest()

  @Rule
  @JvmField
  val logActionsDuringTest = LogActionsDuringTest()

  @get:Rule
  val testRootPath: TemporaryFolder by lazy {
//    TemporaryFolder(File(PathManagerEx.getCommunityHomePath() + PATH_TO_DATA))
//    this.javaClass.classLoader.getResource("com/intellij/ide/projectWizard/kotlin")
    TemporaryFolder()
  }
  val projectFolder: String by lazy {
    testRootPath.newFolder(testMethod.methodName).canonicalPath
  }

//  @Rule
//  @JvmField
//  val collector = object : ErrorCollector() {
//    override fun addError(error: Throwable?) {
//      val screenshotName = testName + "." + testMethod.methodName
//      takeScreenshotOnFailure(error, screenshotName)
//      super.addError(error)
//    }
//  }

  @Before
  fun setUp() {
    guiTestRule.IdeHandling().setUp()
    logStartTest(testMethod.methodName)
    KotlinTestProperties.useKotlinArtifactFromEnvironment()
  }

  @After
  fun tearDown() {
    if (isIdeFrameRun())
      closeProject()
    logEndTest(testMethod.methodName)
    guiTestRule.IdeHandling().tearDown()
  }

  open fun isIdeFrameRun(): Boolean = true
}

fun <T> ErrorCollector.checkThat(value: T, matcher: Matcher<T>, reason: () -> String) {
  checkThat(reason(), value, matcher)
}
