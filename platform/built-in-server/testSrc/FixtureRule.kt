package org.jetbrains.ide

import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.junit.rules.ExternalResource
import javax.swing.SwingUtilities

fun invokeAndWaitIfNeed(runnable: () -> Unit) {
  if (SwingUtilities.isEventDispatchThread()) runnable() else SwingUtilities.invokeAndWait(runnable)
}

inline fun runWriteAction(runnable: () -> Unit) {
  val token = WriteAction.start()
  try {
    runnable()
  }
  finally {
    token.finish()
  }
}

public class FixtureRule() : ExternalResource() {
  val projectFixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder().getFixture()

  override fun before() {
    PlatformTestCase.initPlatformLangPrefix()
    UsefulTestCase.replaceIdeEventQueueSafely()

    invokeAndWaitIfNeed { projectFixture.setUp() }
  }

  override fun after() {
    invokeAndWaitIfNeed { projectFixture.tearDown() }
  }
}