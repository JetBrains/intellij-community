// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.ide.impl.SelectProjectOpenProcessorDialog
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.runBlocking

class AutoLinkTest : AutoLinkTestCase() {
  fun `test auto-link project`() {
    val projectDirectory = createProjectSubDir("project")
    val unlinkedProjectAware = createAndRegisterUnlinkedProjectAware("A", "a")
    createProjectSubFile("project/file.a")

    createDummyCompilerXml("project/.idea/compiler.xml")

    runBlocking {
      openProjectAsync(projectDirectory).useProjectAsync { project ->
        assertNotificationAware(project)
        assertLinkedProjects(unlinkedProjectAware, 1)
      }
    }
  }

  fun `test auto-link project without project model`() {
    val projectDirectory = createProjectSubDir("project")
    val unlinkedProjectAware = createAndRegisterUnlinkedProjectAware("A", "a")
    createProjectSubFile("project/file.a")

    createProjectSubDir("project/.idea")

    runBlocking {
      openProjectAsync(projectDirectory).useProjectAsync { project ->
        assertNotificationAware(project)
        assertLinkedProjects(unlinkedProjectAware, 1)
      }
    }
  }

  fun `test don't auto-link project with project model`() {
    val projectDirectory = createProjectSubDir("project")
    val unlinkedProjectAware = createAndRegisterUnlinkedProjectAware("A", "a")
    createProjectSubFile("project/file.a")

    createDummyCompilerXml("project/.idea/compiler.xml")
    createDummyModulesXml("project/.idea/modules.xml")

    runBlocking {
      openProjectAsync(projectDirectory).useProjectAsync { project ->
        val projectId = unlinkedProjectAware.getProjectId(projectDirectory)
        assertNotificationAware(project, projectId)
        assertLinkedProjects(unlinkedProjectAware, 0)
      }
    }
  }

  fun `test don't auto-link project with several external systems`() {
    val projectDirectory = createProjectSubDir("project")
    val unlinkedProjectAwareA = createAndRegisterUnlinkedProjectAware("A", "a")
    val unlinkedProjectAwareB = createAndRegisterUnlinkedProjectAware("B", "b")
    createProjectSubFile("project/file.a")
    createProjectSubFile("project/file.b")

    createProjectSubDir("project/.idea")

    runBlocking {
      openProjectAsync(projectDirectory).useProjectAsync { project ->
        val projectIdA = unlinkedProjectAwareA.getProjectId(projectDirectory)
        val projectIdB = unlinkedProjectAwareB.getProjectId(projectDirectory)
        assertNotificationAware(project, projectIdA, projectIdB)
        assertLinkedProjects(unlinkedProjectAwareA, 0)
        assertLinkedProjects(unlinkedProjectAwareB, 0)
      }
    }
  }

  fun `test don't auto-link project if has linked projects`() {
    val projectDirectory = createProjectSubDir("project")
    val unlinkedProjectAwareA = createAndRegisterUnlinkedProjectAware("A", "a")
    val unlinkedProjectAwareB = createAndRegisterUnlinkedProjectAware("B", "b")
    createProjectSubFile("project/file.a")
    createProjectSubFile("project/file.b")

    createProjectSubDir("project/.idea")
    unlinkedProjectAwareA.linkProject(projectDirectory.path)

    runBlocking {
      openProjectAsync(projectDirectory).useProjectAsync { project ->
        val projectIdB = unlinkedProjectAwareB.getProjectId(projectDirectory)
        assertNotificationAware(project, projectIdB)
        assertLinkedProjects(unlinkedProjectAwareA, 1)
        assertLinkedProjects(unlinkedProjectAwareB, 0)
      }
    }
  }

  fun `test don't auto-link project if opened and linked project by unknown open processor`() {
    val projectDirectory = createProjectSubDir("project")
    val unlinkedProjectAwareA = createUnlinkedProjectAware("A", "a")
    val unlinkedProjectAwareB = createAndRegisterUnlinkedProjectAware("B", "b")
    createProjectSubFile("project/file.a")
    createProjectSubFile("project/file.b")

    val projectOpenProcessorA = createAndRegisterProjectOpenProcessor(unlinkedProjectAwareA, false)
    val projectOpenProcessorB = createAndRegisterProjectOpenProcessor(unlinkedProjectAwareB)
    SelectProjectOpenProcessorDialog.setTestDialog({ processors, _ ->
      assertUnorderedElementsAreEqual(processors, projectOpenProcessorA, projectOpenProcessorB)
      projectOpenProcessorA
    }, testDisposable)

    runBlocking {
      openProjectAsync(projectDirectory).useProjectAsync { project ->
        val projectIdB = unlinkedProjectAwareB.getProjectId(projectDirectory)
        assertNotificationAware(project, projectIdB)
        assertLinkedProjects(unlinkedProjectAwareA, 1)
        assertLinkedProjects(unlinkedProjectAwareB, 0)
      }
    }
  }
}