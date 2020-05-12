// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class SdkInRootModelTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  lateinit var module: Module

  @Before
  fun setUp() {
    module = projectModel.createModule()
  }

  @Test
  fun `set module sdk`() {
    val model = createModifiableModel(module)
    val sdk = projectModel.addSdk(projectModel.createSdk("my sdk"))
    model.sdk = sdk
    assertThat(model.isSdkInherited).isFalse()
    assertThat(model.sdk).isEqualTo(sdk)
    assertThat(model.sdkName).isEqualTo("my sdk")
    val committed = commitModifiableRootModel(model)
    assertThat(committed.isSdkInherited).isFalse()
    assertThat(committed.sdk).isEqualTo(sdk)

    val cleared = commitModifiableRootModel(createModifiableModel(module).also { it.clear() }, assertChanged = false)
    assertThat(cleared.isSdkInherited).isFalse()
    assertThat(cleared.sdk).isEqualTo(sdk)
  }

  @Test
  fun `inherit project sdk`() {
    val sdk = projectModel.addSdk(projectModel.createSdk("my sdk"))
    runWriteActionAndWait { projectModel.projectRootManager.projectSdk = sdk }
    val model = createModifiableModel(module)
    model.inheritSdk()
    assertThat(model.isSdkInherited).isTrue()
    assertThat(model.sdk).isEqualTo(sdk)
    assertThat(model.sdkName).isEqualTo("my sdk")
    val committed = commitModifiableRootModel(model)
    assertThat(committed.isSdkInherited).isTrue()
    assertThat(committed.sdk).isEqualTo(sdk)

    val cleared = commitModifiableRootModel(createModifiableModel(module).also { it.clear() })
    assertThat(cleared.isSdkInherited).isFalse()
    assertThat(cleared.sdk).isEqualTo(sdk)
  }

  @Test
  fun `set not yet added sdk as module sdk`() {
    val model = createModifiableModel(module)
    val sdk = projectModel.createSdk("my sdk")
    model.sdk = sdk
    assertThat(model.isSdkInherited).isFalse()
    assertThat(model.sdk).isEqualTo(sdk)
    assertThat(model.sdkName).isEqualTo("my sdk")
    val committed = commitModifiableRootModel(model)
    projectModel.addSdk(sdk)
    assertThat(committed.isSdkInherited).isFalse()
    assertThat(committed.sdk).isEqualTo(sdk)
  }

  @Test
  fun `set module sdk by name`() {
    val model = createModifiableModel(module)
    model.setInvalidSdk("my sdk", projectModel.sdkType.name)
    assertThat(model.isSdkInherited).isFalse()
    assertThat(model.sdk).isNull()
    assertThat(model.sdkName).isEqualTo("my sdk")
    val committed = commitModifiableRootModel(model)
    assertThat(committed.isSdkInherited).isFalse()
    assertThat(committed.sdk).isNull()
    val sdk = projectModel.addSdk(projectModel.createSdk("my sdk"))
    assertThat(committed.sdk).isEqualTo(sdk)
  }

  @Test
  fun `inherit project sdk by name`() {
    runWriteActionAndWait { projectModel.projectRootManager.setProjectSdkName("my sdk", projectModel.sdkType.name) }
    val model = createModifiableModel(module)
    model.inheritSdk()
    assertThat(model.isSdkInherited).isTrue()
    assertThat(model.sdk).isNull()
    assertThat(model.sdkName).isEqualTo("my sdk")
    val committed = commitModifiableRootModel(model)
    assertThat(committed.isSdkInherited).isTrue()
    assertThat(committed.sdk).isNull()
    val sdk = projectModel.addSdk(projectModel.createSdk("my sdk"))
    assertThat(committed.sdk).isEqualTo(sdk)
  }

  @Test
  fun `set module sdk from accessor`() {
    val sdk = projectModel.createSdk("my sdk")
    val model = createModifiableModel(module, object : RootConfigurationAccessor() {
      override fun getSdk(existing: Sdk?, sdkName: String?): Sdk? {
        return if (sdkName == "my sdk") sdk else existing
      }
    })
    model.setInvalidSdk("my sdk", projectModel.sdkType.name)
    assertThat(model.sdk).isEqualTo(sdk)
    assertThat(model.sdkName).isEqualTo("my sdk")
    val committed = commitModifiableRootModel(model)
    assertThat(committed.isSdkInherited).isFalse()
    assertThat(committed.sdk).isNull()
    projectModel.addSdk(sdk)
    assertThat(committed.sdk).isEqualTo(sdk)
  }

  @Test
  fun `set project sdk from accessor`() {
    val sdk = projectModel.createSdk("my sdk")
    val model = createModifiableModel(module, object : RootConfigurationAccessor() {
      override fun getProjectSdk(project: Project?): Sdk = sdk
      override fun getProjectSdkName(project: Project?): String? = "my sdk"
    })
    model.inheritSdk()
    assertThat(model.sdk).isEqualTo(sdk)
    assertThat(model.sdkName).isEqualTo("my sdk")
    val committed = commitModifiableRootModel(model)
    assertThat(committed.isSdkInherited).isTrue()
    assertThat(committed.sdk).isNull()
    projectModel.addSdk(sdk)
    runWriteActionAndWait { projectModel.projectRootManager.projectSdk = sdk }
    assertThat(committed.sdk).isEqualTo(sdk)
  }

  @Test
  fun `set project sdk from accessor by name`() {
    val sdk = projectModel.createSdk("my sdk")
    val model = createModifiableModel(module, object : RootConfigurationAccessor() {
      override fun getProjectSdkName(project: Project?): String? = "my sdk"
    })
    model.inheritSdk()
    assertThat(model.sdk).isNull()
    assertThat(model.sdkName).isEqualTo("my sdk")
    val committed = commitModifiableRootModel(model)
    assertThat(committed.isSdkInherited).isTrue()
    assertThat(committed.sdk).isNull()
    projectModel.addSdk(sdk)
    runWriteActionAndWait { projectModel.projectRootManager.projectSdk = sdk }
    assertThat(committed.sdk).isEqualTo(sdk)
  }
}