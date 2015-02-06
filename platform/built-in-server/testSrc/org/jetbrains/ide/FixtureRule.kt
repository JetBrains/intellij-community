package org.jetbrains.ide

import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.ui.UIUtil
import org.junit.rules.ExternalResource

public class FixtureRule : ExternalResource() {
  private var projectFixture: IdeaProjectTestFixture? = null

  throws(javaClass<Throwable>())
  override fun before() {
    PlatformTestCase.initPlatformLangPrefix()

    projectFixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder().getFixture()

    UIUtil.invokeAndWaitIfNeeded(object : Runnable {
      override fun run() {
        try {
          projectFixture!!.setUp()
        }
        catch (e: Exception) {
          throw RuntimeException(e)
        }

      }
    })
  }

  override fun after() {
    UIUtil.invokeAndWaitIfNeeded(object : Runnable {
      override fun run() {
        try {
          projectFixture!!.tearDown()
        }
        catch (e: Exception) {
          throw RuntimeException(e)
        }

      }
    })
  }
}
