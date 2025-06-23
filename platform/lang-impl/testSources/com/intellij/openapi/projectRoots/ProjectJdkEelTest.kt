// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.roots.ui.configuration.SdkTestCase.TestSdkGenerator.SdkInfo
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.roots.ui.configuration.testSdkFixture
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.testFramework.junit5.eel.fixture.eelFixture
import com.intellij.platform.testFramework.junit5.eel.fixture.tempDirFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files


@TestApplication
class ProjectJdkEelTest {
  val eel = eelFixture(EelPlatform.Linux(EelPlatform.Arch.Unknown))

  val localProject = projectFixture(openAfterCreation = true)

  val tempDirFixture = eel.tempDirFixture()
  val eelProject = projectFixture(tempDirFixture, openAfterCreation = true)
  val testSdkGenerator = testSdkFixture()

  @Test
  fun `only relevant jdks are visible`() = timeoutRunBlocking {
    val jdkTable = ProjectJdkTable.getInstance()

    withAddedSdk(jdkTable, "local sdk", EnvKind.Local) {
      withAddedSdk(jdkTable, "eel sdk", EnvKind.Eel) {
        val localModel = getLocalSdkModel()
        val eelModel = getEelSdkModel()

        Assertions.assertThat(localModel.sdks).hasSize(1)
        Assertions.assertThat(eelModel.sdks).hasSize(1)
        Assertions.assertThat(localModel.sdks.intersect(eelModel.sdks.toSet())).isEmpty()
      }
    }
  }

  @Test
  @RegistryKey("ide.workspace.model.per.environment.model.separation", "true")
  fun `multiple SDKs with the same name can exist`() = timeoutRunBlocking {
    val jdkTable = ProjectJdkTable.getInstance()

    val sharedName = "shared sdk name"

    withAddedSdk(jdkTable, sharedName, EnvKind.Local) {
      withAddedSdk(jdkTable, sharedName, EnvKind.Eel) {
        val localModel = getLocalSdkModel()
        val eelModel = getEelSdkModel()

        Assertions.assertThat(localModel.sdks).hasSize(1)
        Assertions.assertThat(eelModel.sdks).hasSize(1)
        Assertions.assertThat(localModel.sdks.intersect(eelModel.sdks.asList())).isEmpty()
        Assertions.assertThat(localModel.sdks[0].name).isEqualTo(eelModel.sdks[0].name)
      }
    }
  }

  @Test
  @RegistryKey("ide.workspace.model.per.environment.model.separation", "true")
  fun `removal of one jdk does not affect another`() = timeoutRunBlocking {
    val jdkTable = ProjectJdkTable.getInstance()

    val sharedName = "shared sdk name"

    withAddedSdk(jdkTable, sharedName, EnvKind.Local) {
      withAddedSdk(jdkTable, sharedName, EnvKind.Eel) {
        val localModel = getLocalSdkModel()
        val eelModel = getEelSdkModel()

        Assertions.assertThat(localModel.sdks).hasSize(1)
        Assertions.assertThat(eelModel.sdks).hasSize(1)

        eelModel.removeSdk(eelModel.sdks[0])
        withContext(Dispatchers.EDT) {
          eelModel.apply()
        }

        Assertions.assertThat(localModel.sdks).hasSize(1)
        Assertions.assertThat(eelModel.sdks).hasSize(0)
        Assertions.assertThat(localModel.sdks[0].name).isEqualTo(sharedName)
      }
    }
  }

  @Test
  @RegistryKey("ide.workspace.model.per.environment.model.separation", "true")
  fun `addition of one jdk does not affect another`() = timeoutRunBlocking {
    val jdkTable = ProjectJdkTable.getInstance()

    val sharedName = "shared sdk name"

    withAddedSdk(jdkTable, sharedName, EnvKind.Eel) {
      val localModel = getLocalSdkModel()
      val eelModel = getEelSdkModel()


      val newSdk = testSdkGenerator.get().createTestSdk(SdkInfo(sharedName, "1", Files.createTempDirectory(sharedName).toString()))
      Assertions.assertThat(localModel.sdks).hasSize(0)
      Assertions.assertThat(eelModel.sdks).hasSize(1)

      edtWriteAction {
        jdkTable.addJdk(newSdk)
      }
      try {
        withContext(Dispatchers.EDT) {
          localModel.reset(localProject.get())
        }
        Assertions.assertThat(localModel.sdks).hasSize(1)
        Assertions.assertThat(eelModel.sdks).hasSize(1)
        Assertions.assertThat(localModel.sdks[0].homePath).isEqualTo(newSdk.homePath)
        Assertions.assertThat(eelModel.sdks[0].homePath).isNotEqualTo(newSdk.homePath)
      }
      finally {
        edtWriteAction {
          jdkTable.removeJdk(newSdk)
        }
      }
    }
  }

  @Test
  @RegistryKey("ide.workspace.model.per.environment.model.separation", "false")
  fun `models are fine when separation is disabled`() = timeoutRunBlocking {
    val localJdkTableView = ProjectJdkTable.getInstance(localProject.get())
    val eelJdkTableView = ProjectJdkTable.getInstance(eelProject.get())

    withAddedSdk(localJdkTableView, "local sdk", EnvKind.Local) {
      withAddedSdk(eelJdkTableView, "eel sdk", EnvKind.Eel) {
        val localModel = getLocalSdkModel()
        val eelModel = getEelSdkModel()
        Assertions.assertThat(localModel.sdks).hasSize(1)
        Assertions.assertThat(eelModel.sdks).hasSize(1)
      }
    }
  }


  @Test
  @RegistryKey("ide.workspace.model.per.environment.model.separation", "true")
  fun `different views of ProjectJdkTable are independent`() = timeoutRunBlocking {
    val globalTable = ProjectJdkTable.getInstance()
    val localJdkTableView = ProjectJdkTable.getInstance(localProject.get())
    val eelJdkTableView = ProjectJdkTable.getInstance(eelProject.get())

    withAddedSdk(localJdkTableView, "local sdk", EnvKind.Local) {
      withAddedSdk(eelJdkTableView, "eel sdk", EnvKind.Eel) {
        val allJdks = globalTable.allJdks.toList()
        val allLocalJdks = localJdkTableView.allJdks.toList()
        val allEelJdks = eelJdkTableView.getAllJdks().toList()
        Assertions.assertThat(allJdks).hasSize(2)
        Assertions.assertThat(allLocalJdks).hasSize(1)
        Assertions.assertThat(allEelJdks).hasSize(1)
        Assertions.assertThat(allLocalJdks[0]).isNotEqualTo(allEelJdks[0])
        Assertions.assertThat(allLocalJdks).allMatch { it in allJdks }
        Assertions.assertThat(allEelJdks).allMatch { it in allJdks }
      }
    }
  }

  @Test
  @RegistryKey("ide.workspace.model.per.environment.model.separation", "true")
  fun `sdks are identifiable by their name in different views`() = timeoutRunBlocking {
    val globalTable = ProjectJdkTable.getInstance()
    val localJdkTableView = ProjectJdkTable.getInstance(localProject.get())
    val eelJdkTableView = ProjectJdkTable.getInstance(eelProject.get())

    val commonName = "common sdk name"
    withAddedSdk(globalTable, commonName, EnvKind.Local) {
      withAddedSdk(globalTable, commonName, EnvKind.Eel) {
        val localSdk = localJdkTableView.findJdk(commonName)!!
        val eelSdk = eelJdkTableView.findJdk(commonName)!!
        Assertions.assertThat(localSdk.name).isEqualTo(eelSdk.name)
        Assertions.assertThat(localSdk.homePath).isNotEqualTo(eelSdk.homePath)
      }
    }
  }

  enum class EnvKind {
    Eel,
    Local
  }

  private suspend fun withAddedSdk(table: ProjectJdkTable, name: String, kind: EnvKind, block: suspend CoroutineScope.() -> Unit) {
    coroutineScope {
      val tempDir = when (kind) {
        EnvKind.Eel -> EelPathUtils.createTemporaryDirectory(eelProject.get())
        EnvKind.Local -> Files.createTempDirectory(name)
      }
      val sdk = testSdkGenerator.get().createTestSdk(SdkInfo(name, "1", tempDir.toString()))
      edtWriteAction {
        table.addJdk(sdk)
      }
      try {
        block()
      }
      finally {
        edtWriteAction {
          table.removeJdk(sdk)
        }
      }
    }
  }

  private suspend fun getLocalSdkModel(): ProjectSdksModel {
    return ProjectSdksModel().apply {
      withContext(Dispatchers.EDT) {
        reset(localProject.get())
      }
    }
  }

  private suspend fun getEelSdkModel(): ProjectSdksModel {
    return ProjectSdksModel().apply {
      withContext(Dispatchers.EDT) {
        reset(eelProject.get())
      }
    }
  }
}