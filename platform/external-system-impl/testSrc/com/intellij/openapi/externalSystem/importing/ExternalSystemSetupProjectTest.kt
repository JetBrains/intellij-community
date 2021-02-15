// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.importing

import org.junit.Test

interface ExternalSystemSetupProjectTest : ExternalSystemSetupProjectTestCase {
  @Test
  fun `test project open`() {
    val projectInfo = generateProject("A")
    waitForImport {
      openProjectFrom(projectInfo.projectFile)
    }.use {
      assertModules(it, projectInfo)
      assertDefaultProjectSettings(it)
      assertDefaultProjectState(it)
    }
  }

  @Test
  fun `test project import`() {
    val projectInfo = generateProject("A")
    waitForImport {
      importProjectFrom(projectInfo.projectFile)
    }.use {
      assertModules(it, projectInfo)
      assertDefaultProjectSettings(it)
      assertDefaultProjectState(it)
    }
  }

  @Test
  fun `test project attach`() {
    val projectInfo = generateProject("A")
    openPlatformProjectFrom(projectInfo.projectFile.parent).use {
      waitForImport {
        attachProject(it, projectInfo.projectFile)
      }
      assertModules(it, projectInfo)
      assertDefaultProjectSettings(it)
      assertDefaultProjectState(it)
    }
  }

  @Test
  fun `test project import from script`() {
    val projectInfo = generateProject("A")
    openPlatformProjectFrom(projectInfo.projectFile.parent).use {
      waitForImport {
        attachProjectFromScript(it, projectInfo.projectFile)
      }
      assertModules(it, projectInfo)
      assertDefaultProjectSettings(it)
      assertDefaultProjectState(it)
    }
  }

  @Test
  fun `test module attach`() {
    val projectInfo = generateProject("A")
    val linkedProjectInfo = generateProject("L")
    waitForImport {
      openProjectFrom(projectInfo.projectFile)
    }.use {
      assertModules(it, projectInfo)
      waitForImport {
        attachProject(it, linkedProjectInfo.projectFile)
      }
      assertModules(it, projectInfo, linkedProjectInfo)
      assertDefaultProjectSettings(it)
      assertDefaultProjectState(it)
    }
  }

  @Test
  fun `test project re-open`() {
    val projectInfo = generateProject("A")
    val linkedProjectInfo = generateProject("L")
    waitForImport {
      openProjectFrom(projectInfo.projectFile)
    }.use(save = true) {
      assertModules(it, projectInfo)
      waitForImport {
        attachProject(it, linkedProjectInfo.projectFile)
      }
      assertModules(it, projectInfo, linkedProjectInfo)
      assertDefaultProjectSettings(it)
      assertDefaultProjectState(it)
    }
    openProjectFrom(projectInfo.projectFile).use {
      assertModules(it, projectInfo, linkedProjectInfo)
      assertDefaultProjectSettings(it)
      assertDefaultProjectState(it)
    }
  }

  @Test
  fun `test project re-import deprecation`() {
    val projectInfo = generateProject("A")
    val linkedProjectInfo = generateProject("L")

    waitForImport {
      openProjectFrom(projectInfo.projectFile)
    }.use(save = true) {
      assertModules(it, projectInfo)
      waitForImport {
        attachProject(it, linkedProjectInfo.projectFile)
      }
      assertModules(it, projectInfo, linkedProjectInfo)
      assertDefaultProjectSettings(it)
      assertDefaultProjectState(it)
    }
    importProjectFrom(projectInfo.projectFile).use {
      assertModules(it, projectInfo, linkedProjectInfo)
      assertDefaultProjectSettings(it)
      assertDefaultProjectState(it)
    }
  }
}