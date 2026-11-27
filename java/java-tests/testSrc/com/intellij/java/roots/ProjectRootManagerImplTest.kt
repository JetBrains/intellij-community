// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots

import com.intellij.idea.TestFor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.TemporaryDirectoryExtension
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.createOrLoadProject
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.writeChild
import com.intellij.util.concurrency.ThreadingAssertions
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

@TestApplication
class ProjectRootManagerImplTest {
  @JvmField
  @RegisterExtension
  val tempDirManager = TemporaryDirectoryExtension()

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
      edtWriteAction {
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
  fun testNoEventsIfNothingChanged(): Unit = timeoutRunBlocking {
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
      edtWriteAction {
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