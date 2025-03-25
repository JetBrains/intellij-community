// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots

import com.intellij.idea.TestFor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl.Companion.getInstanceImpl
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@TestApplication
class ProjectRootManagerImplTest {

  val project = projectFixture(openAfterCreation = true)

  @Test
  @TestFor(issues = ["IDEA-232634"])
  fun testLoadStateFiresJdkChange(): Unit = timeoutRunBlocking {
    val project = project.get()
    val count = AtomicInteger(0)
    ProjectRootManagerEx.getInstanceEx(project).addProjectJdkListener {
      ThreadingAssertions.assertWriteAccess()
      count.incrementAndGet()
    }

    val impl = getInstanceImpl(project)
    val firstLoad = JDOMUtil.load("""
                                   <component name="ProjectRootManager" version="2" languageLevel="JDK_11" default="true" project-jdk-name="corretto-11" project-jdk-type="JavaSDK">
                                     <output url="file://${'$'}PROJECT_DIR${'$'}/out" />
                                   </component>
                                   """.trimIndent())
    val secondLoad = JDOMUtil.load("""
                                   <component name="ProjectRootManager" version="2" languageLevel="JDK_11" default="true" project-jdk-name="corretto-11" project-jdk-type="JavaSDK">
                                     <output url="file://${'$'}PROJECT_DIR${'$'}/out2" />
                                   </component>
                                   """.trimIndent())
    impl.loadState(firstLoad)
    awaitWriteActions()

    impl.loadState(secondLoad)
    awaitWriteActions()

    Assertions.assertThat(count).hasValueGreaterThanOrEqualTo(2)
  }

  @Test
  @TestFor(issues = ["IDEA-330499"])
  fun testNoEventsIfNothingChanged() = timeoutRunBlocking {
    val project = project.get()
    val count = AtomicInteger(0)
    ProjectRootManagerEx.getInstanceEx(project).addProjectJdkListener {
      ThreadingAssertions.assertWriteAccess()
      count.incrementAndGet()
    }

    val impl = getInstanceImpl(project)
    val firstLoad = JDOMUtil.load("""
                                        <component name="ProjectRootManager" version="2" languageLevel="JDK_11" default="true" project-jdk-name="corretto-11" project-jdk-type="JavaSDK">
                                          <output url="file://${'$'}PROJECT_DIR${'$'}/out" />
                                        </component>
                                        """.trimIndent())
    val secondLoad = JDOMUtil.load("""
                                         <component name="ProjectRootManager" version="2" languageLevel="JDK_11" default="true" project-jdk-name="corretto-11" project-jdk-type="JavaSDK">
                                           <output url="file://${'$'}PROJECT_DIR${'$'}/out2" />
                                         </component>
                                         """.trimIndent())
    impl.loadState(firstLoad)
    waitUntil {
      1 == count.get()
    }

    impl.loadState(secondLoad)
    awaitWriteActions()
    waitUntil {
      2 == count.get()
    }

    impl.loadState(secondLoad)
    awaitWriteActions()
    repeat(5) {
      org.junit.jupiter.api.Assertions.assertEquals(2, count.get())
      delay(1.seconds)
    }
  }

  private suspend fun awaitWriteActions() {
    delay(100)
    readAction { } // custom read action in order to dispatch pending WAs; it will execute after all pending WAs
  }

}