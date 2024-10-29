// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.ide.impl.SelectProjectOpenProcessorDialog
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.testFramework.utils.vfs.createFile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class AutoLinkTest : AutoLinkTestCase() {

  @Test
  fun `test auto-link project`() {
    runBlocking {
      writeAction {
        testRoot.createFile("project/file.a")
        testRoot.createFile("project/.idea/compiler.xml")
          .writeText("""
            |<?xml version="1.0" encoding="UTF-8"?>
            |<project version="4">
            |  <component name="CompilerConfiguration">
            |    <bytecodeTargetLevel target="14" />
            |  </component>
            |</project>
          """.trimMargin())
      }

      val unlinkedProjectAware = createAndRegisterUnlinkedProjectAware("A", "a")

      openProject("project")
        .useProjectAsync { project ->
          assertNotificationAware(project)
          assertLinkedProjects(unlinkedProjectAware, 1)
        }
    }
  }

  @Test
  fun `test auto-link project without project model`() {
    runBlocking {
      writeAction {
        testRoot.createFile("project/file.a")
        testRoot.createDirectory("project/.idea")
      }

      val unlinkedProjectAware = createAndRegisterUnlinkedProjectAware("A", "a")

      openProject("project")
        .useProjectAsync { project ->
          assertNotificationAware(project)
          assertLinkedProjects(unlinkedProjectAware, 1)
        }
    }
  }

  @Test
  fun `test don't auto-link project with project model`() {
    runBlocking {
      writeAction {
        testRoot.createFile("project/file.a")
        testRoot.createFile("project/.idea/compiler.xml")
          .writeText("""
          |<?xml version="1.0" encoding="UTF-8"?>
          |<project version="4">
          |  <component name="CompilerConfiguration">
          |    <bytecodeTargetLevel target="14" />
          |  </component>
          |</project>
        """.trimMargin())
        testRoot.createFile("project/.idea/modules.xml")
          .writeText("""
            |<?xml version="1.0" encoding="UTF-8"?>
            |<project version="4">
            |  <component name="ProjectModuleManager">
            |    <modules>
            |      <module fileurl="file://${'$'}PROJECT_DIR${'$'}/project.iml" filepath="${'$'}PROJECT_DIR${'$'}/project.iml" />
            |    </modules>
            |  </component>
            |</project>
          """.trimMargin())
      }

      val unlinkedProjectAware = createAndRegisterUnlinkedProjectAware("A", "a")

      openProject("project")
        .useProjectAsync { project ->
          assertNotificationAware(project, "A" to "project")
          assertLinkedProjects(unlinkedProjectAware, 0)
        }
    }
  }

  @Test
  fun `test don't auto-link project with several external systems`() {
    runBlocking {
      writeAction {
        testRoot.createFile("project/file.a")
        testRoot.createFile("project/file.b")
        testRoot.createDirectory("project/.idea")
      }

      val unlinkedProjectAwareA = createAndRegisterUnlinkedProjectAware("A", "a")
      val unlinkedProjectAwareB = createAndRegisterUnlinkedProjectAware("B", "b")

      openProject("project")
        .useProjectAsync { project ->
          assertNotificationAware(project, "A" to "project", "B" to "project")
          assertLinkedProjects(unlinkedProjectAwareA, 0)
          assertLinkedProjects(unlinkedProjectAwareB, 0)
        }
    }
  }

  @Test
  fun `test don't auto-link project if has linked projects`() {
    runBlocking {
      writeAction {
        testRoot.createFile("project/file.a")
        testRoot.createFile("project/file.b")
        testRoot.createDirectory("project/.idea")
      }

      val unlinkedProjectAwareA = createAndRegisterUnlinkedProjectAware("A", "a")
      val unlinkedProjectAwareB = createAndRegisterUnlinkedProjectAware("B", "b")
      unlinkedProjectAwareA.linkProject(testRoot.path + "/project")

      openProject("project")
        .useProjectAsync { project ->
          assertLinkedProjects(unlinkedProjectAwareA, 1)
          assertLinkedProjects(unlinkedProjectAwareB, 0)
        }
    }
  }

  @Test
  fun `test don't auto-link project if opened and linked project by unknown open processor`() {
    runBlocking {
      writeAction {
        testRoot.createFile("project/file.a")
        testRoot.createFile("project/file.b")
      }

      val unlinkedProjectAwareA = createUnlinkedProjectAware("A", "a")
      val unlinkedProjectAwareB = createAndRegisterUnlinkedProjectAware("B", "b")
      val projectOpenProcessorA = createAndRegisterProjectOpenProcessor(unlinkedProjectAwareA, false)
      val projectOpenProcessorB = createAndRegisterProjectOpenProcessor(unlinkedProjectAwareB)

      SelectProjectOpenProcessorDialog.setTestDialog(testDisposable) { processors, _ ->
        Assertions.assertEquals(setOf(projectOpenProcessorA, projectOpenProcessorB), processors.toSet())
        projectOpenProcessorA
      }

      openProject("project")
        .useProjectAsync { project ->
          assertNotificationAware(project, "B" to "project")
          assertLinkedProjects(unlinkedProjectAwareA, 1)
          assertLinkedProjects(unlinkedProjectAwareB, 0)
        }
    }
  }
}