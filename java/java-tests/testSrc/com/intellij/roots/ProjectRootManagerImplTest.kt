// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots

import com.intellij.idea.TestFor
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl.Companion.getInstanceImpl
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.executeSomeCoroutineTasksAndDispatchAllInvocationEvents
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class ProjectRootManagerImplTest : HeavyPlatformTestCase() {
  @TestFor(issues = ["IDEA-232634"])
  fun testLoadStateFiresJdkChange() {
    val count = AtomicInteger(0)
    ProjectRootManagerEx.getInstanceEx(myProject).addProjectJdkListener {
      ThreadingAssertions.assertWriteAccess()
      count.incrementAndGet()
    }

    val impl = getInstanceImpl(myProject)
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
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(myProject)
    impl.loadState(secondLoad)
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(myProject)

    Assertions.assertThat(count).hasValueGreaterThanOrEqualTo(2)
  }

  @TestFor(issues = ["IDEA-330499"])
  fun testNoEventsIfNothingChanged() = runBlocking {
    val count = AtomicInteger(0)
    ProjectRootManagerEx.getInstanceEx(myProject).addProjectJdkListener {
      ThreadingAssertions.assertWriteAccess()
      count.incrementAndGet()
    }

    val impl = getInstanceImpl(myProject)
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
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(myProject)
    waitUntil {
      1 == count.get()
    }

    impl.loadState(secondLoad)
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(myProject)
    waitUntil {
      2 == count.get()
    }

    impl.loadState(secondLoad)
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(myProject)
    repeat(10) {
      assertEquals(2, count.get())
      delay(1.seconds)
    }
  }
}
