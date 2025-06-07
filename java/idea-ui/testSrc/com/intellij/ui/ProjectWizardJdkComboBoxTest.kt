// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.projectWizard.ProjectWizardJdkComboBox
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.testFramework.junit5.eel.fixture.eelFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.utils.io.deleteRecursively
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class ProjectWizardJdkComboBoxTest {
  val eelFixture = eelFixture(EelPlatform.Linux(EelPlatform.Arch.Unknown))

  @Test
  fun `changing eel changes available sdks`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val localSdk = createLocalSdk(disposable)
    val eelSdk = createEelSdk(disposable)

    val comboBox = ProjectWizardJdkComboBox(null, disposable)

    comboBox.eelChanged(LocalEelDescriptor)
    Assertions.assertTrue(comboBox.contains(localSdk))
    Assertions.assertFalse(comboBox.contains(eelSdk))

    comboBox.eelChanged(eelFixture.get().eelDescriptor)
    Assertions.assertFalse(comboBox.contains(localSdk))
    Assertions.assertTrue(comboBox.contains(eelSdk))
  }


  suspend fun createSdk(path: Path, name: String, disposable: Disposable): Sdk {
    val jdkTable = ProjectJdkTable.getInstance()
    val javaSdkType = SimpleJavaSdkType.getInstance()
    Disposer.register(disposable) {
      path.deleteRecursively()
    }
    val sdk = jdkTable.createSdk(name, javaSdkType)
    val modificator = sdk.sdkModificator
    modificator.homePath = path.toString()
    edtWriteAction {
      modificator.commitChanges()
      jdkTable.addJdk(sdk, disposable)
    }
    return sdk
  }

  suspend fun createLocalSdk(disposable: Disposable): Sdk {
    val tempDir = Files.createTempDirectory("test-")
    return createSdk(tempDir, "local-sdk", disposable)
  }

  suspend fun createEelSdk(disposable: Disposable): Sdk {
    val eel = eelFixture.get()
    val tempDir = EelPathUtils.createTemporaryDirectory(eel.eelApi)
    return createSdk(tempDir, "eel-sdk", disposable)
  }

  fun ProjectWizardJdkComboBox.contains(sdk: Sdk): Boolean {
    return model.items.find { it is ProjectWizardJdkIntent.ExistingJdk && it.jdk == sdk } != null
  }

}