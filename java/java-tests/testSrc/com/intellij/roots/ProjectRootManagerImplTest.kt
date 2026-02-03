// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots

import com.intellij.idea.TestFor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.createOrLoadProject
import com.intellij.testFramework.writeChild
import com.intellij.util.concurrency.ThreadingAssertions
import org.assertj.core.api.Assertions
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

class ProjectRootManagerImplTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val tempDirManager = TemporaryDirectory()

  @Test
  @TestFor(issues = ["IDEA-232634"])
  fun testMiscFileChangeFiresJdkChangeEvent(): Unit = timeoutRunBlocking(100.minutes) {
    createOrLoadProject(tempDirManager, {
      it.writeChild("${Project.DIRECTORY_STORE_FOLDER}/misc.xml", $$"""
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="ProjectRootManager" version="2" languageLevel="JDK_11" default="true" project-jdk-name="corretto-11" project-jdk-type="JavaSDK">
            <output url="file://$PROJECT_DIR$/out" />
          </component>
        </project>
        """.trimIndent())
      Path.of(it.path)
    }, directoryBased = true, loadComponentState = false) { project ->
      val count = AtomicInteger(0)
      ProjectRootManagerEx.getInstanceEx(project).addProjectJdkListener {
        ThreadingAssertions.assertWriteAccess()
        count.incrementAndGet()
      }

      Assertions.assertThat(count).hasValue(0)
      Assertions.assertThat(ProjectRootManagerEx.getInstanceEx(project).projectSdkName).isEqualTo("corretto-11")

      val projectFile = project.projectFile!!
      writeAction {
        // change: different JDK, same compiler output => should generate JDK change event
        projectFile.writeText($$"""
          <?xml version="1.0" encoding="UTF-8"?>
          <project version="4">
            <component name="ProjectRootManager" version="2" languageLevel="JDK_11" default="true" project-jdk-name="corretto-17" project-jdk-type="JavaSDK">
              <output url="file://$PROJECT_DIR$/out" />
            </component>
          </project>
          """.trimIndent())
      }

      waitUntil {
        "corretto-17" == ProjectRootManagerEx.getInstanceEx(project).projectSdkName
      }

      Assertions.assertThat(count).hasValue(1)
    }
  }

  @Test
  @TestFor(issues = ["IDEA-330499"])
  fun testNoEventsIfNothingChanged() = timeoutRunBlocking {
    createOrLoadProject(tempDirManager, {
      it.writeChild("${Project.DIRECTORY_STORE_FOLDER}/misc.xml", $$"""
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="ProjectRootManager" version="2" languageLevel="JDK_11" default="true" project-jdk-name="corretto-11" project-jdk-type="JavaSDK">
            <output url="file://$PROJECT_DIR$/out" />
          </component>
        </project>
        """.trimIndent())
      Path.of(it.path)
    }, directoryBased = true, loadComponentState = false) { project ->
      val count = AtomicInteger(0)
      ProjectRootManagerEx.getInstanceEx(project).addProjectJdkListener {
        ThreadingAssertions.assertWriteAccess()
        count.incrementAndGet()
      }

      Assertions.assertThat(count).hasValue(0)
      Assertions.assertThat(ProjectRootManagerEx.getInstanceEx(project).projectSdkName).isEqualTo("corretto-11")

      val projectFile = project.projectFile!!
      writeAction {
        // change: same JDK, different compiler output => should be no JDK change event
        projectFile.writeText($$"""
          <?xml version="1.0" encoding="UTF-8"?>
          <project version="4">
            <component name="ProjectRootManager" version="2" languageLevel="JDK_11" default="true" project-jdk-name="corretto-11" project-jdk-type="JavaSDK">
              <output url="file://$PROJECT_DIR$/out2" />
            </component>
          </project>
          """.trimIndent())
      }

      Assertions.assertThat(count).hasValue(0)
    }
  }

  private suspend fun awaitWriteActions() {
    readAction { } // custom read action in order to dispatch pending WAs; it will execute after all pending WAs
  }
}