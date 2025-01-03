// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.roots.ui.configuration.SdkTestCase.TestSdkGenerator
import com.intellij.openapi.roots.ui.configuration.SdkTestCase.TestSdkGenerator.SdkInfo
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.roots.ui.configuration.testSdkFixture
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.platform.testFramework.junit5.eel.fixture.eelFixture
import com.intellij.platform.testFramework.junit5.eel.fixture.tempDirFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files


@TestApplication
class ProjectJdkEelTest {
  val eel = eelFixture(EelPath.OS.UNIX)

  val localProject = projectFixture(openAfterCreation = true)

  val tempDirFixture = eel.tempDirFixture()
  val eelProject = projectFixture(tempDirFixture, openAfterCreation = true)
  val testSdk = testSdkFixture()

  @Test
  fun `only relevant jdks are visible`() = timeoutRunBlocking {
    val jdkTable = ProjectJdkTable.getInstance()

    val localTempDirectory = Files.createTempDirectory("local-sdk")
    val localSdk = TestSdkGenerator.createTestSdk(SdkInfo("local sdk", "1", localTempDirectory.toString()))
    writeAction {
      jdkTable.addJdk(localSdk)
    }

    val eelTempDirectory = EelPathUtils.createTemporaryDirectory(eelProject.get())
    val eelSdk = testSdk.get().createTestSdk(SdkInfo("eel sdk", "1", eelTempDirectory.toString()))
    writeAction {
      jdkTable.addJdk(eelSdk)
    }


    val localModel = ProjectSdksModel().apply {
      reset(localProject.get())
    }

    val eelModel = ProjectSdksModel().apply {
      reset(eelProject.get())
    }

    try {
      Assertions.assertTrue { localModel.sdks.size == 1 }
      Assertions.assertTrue { eelModel.sdks.size == 1 }
      Assertions.assertTrue {
        localModel.sdks.intersect(eelModel.sdks.asList()).isEmpty()
      }
    }
    finally {
      writeAction {
        jdkTable.removeJdk(localSdk)
        jdkTable.removeJdk(eelSdk)
      }
    }
  }
}