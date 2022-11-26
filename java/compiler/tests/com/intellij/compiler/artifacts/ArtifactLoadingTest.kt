// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.artifacts

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider
import com.intellij.packaging.elements.PackagingElementFactory
import com.intellij.packaging.impl.artifacts.PlainArtifactType
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.bridgeEntities.ArtifactEntity
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File

class ArtifactLoadingTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Rule
  @JvmField
  var disposableRule = DisposableRule()

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Test
  fun `loading of default artifact properties`() {
    runWithRegisteredExtension(MockArtifactPropertiesProvider(), ArtifactPropertiesProvider.EP_NAME) {
      val project = projectModel.project

      val artifactManager = ArtifactManager.getInstance(project)
      WriteAction.runAndWait<RuntimeException> {
        artifactManager.addArtifact("Artifact", PlainArtifactType.getInstance(),
          PackagingElementFactory.getInstance().createArtifactRootElement())
      }

      PlatformTestUtil.saveProject(project)

      assertArtifactFileTextEquals("""
          |<component name="ArtifactManager">
          |  <artifact name="Artifact">
          |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
          |    <root id="root" />
          |  </artifact>
          |</component>""".trimMargin())
      val project1 = PlatformTestUtil.loadAndOpenProject(projectModel.baseProjectDir.rootPath, disposableRule.disposable)

      val defaultProperty = runReadAction {
        ArtifactManager.getInstance(project1).artifacts.single().getProperties(MockArtifactPropertiesProvider.getInstance())
      }
      assertNotNull(defaultProperty)
    }
  }

  @Test
  fun `loading of properties with changes`() {
    runWithRegisteredExtension(MockArtifactPropertiesProvider(), ArtifactPropertiesProvider.EP_NAME) {
      val project = projectModel.project

      val artifactManager = ArtifactManager.getInstance(project)
      WriteAction.runAndWait<RuntimeException> {
        val addArtifact = artifactManager.addArtifact("Artifact", PlainArtifactType.getInstance(),
          PackagingElementFactory.getInstance().createArtifactRootElement())
        val modifiableModel = ArtifactManager.getInstance(project).createModifiableModel()
        val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(addArtifact)
        modifiableArtifact.setProperties(MockArtifactPropertiesProvider.getInstance(), MockArtifactProperties().also { it.data = "123" })
        modifiableModel.commit()
      }

      PlatformTestUtil.saveProject(project)

      assertArtifactFileTextEquals("""
          |<component name="ArtifactManager">
          |  <artifact name="Artifact">
          |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
          |    <properties id="mock-properties">
          |      <options>
          |        <option name="data" value="123" />
          |      </options>
          |    </properties>
          |    <root id="root" />
          |  </artifact>
          |</component>""".trimMargin())
      val project1 = PlatformTestUtil.loadAndOpenProject(projectModel.baseProjectDir.rootPath, disposableRule.disposable)

      val defaultProperty = WorkspaceModel.getInstance(project1).entityStorage.current
        .entities(ArtifactEntity::class.java)
        .single()
        .customProperties
        .filter { it.providerType == MockArtifactPropertiesProvider.getInstance().id }
        .single()
      assertTrue("123" in defaultProperty.propertiesXmlTag!!)

      val properties = runReadAction { ArtifactManager.getInstance(project).artifacts.single().getProperties(MockArtifactPropertiesProvider.getInstance()) }
      assertEquals("123", (properties as MockArtifactProperties).data)
    }
  }

  private fun assertArtifactFileTextEquals(expectedText: String) {
    assertEquals(StringUtil.convertLineSeparators(expectedText),
      StringUtil.convertLineSeparators(File(projectModel.baseProjectDir.root, ".idea/artifacts/Artifact.xml").readText()))
  }

  private inline fun <T : Any> runWithRegisteredExtension(extension: T, extensionPoint: ExtensionPointName<T>, action: () -> Unit) {
    val disposable = Disposer.newDisposable()
    runInEdt {
      runWriteAction {
        registerExtension(extension, extensionPoint, disposable)
      }
    }
    try {
      action()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private fun <T : Any> registerExtension(type: T, extensionPointName: ExtensionPointName<T>, disposable: Disposable) {
    val artifactTypeDisposable = Disposer.newDisposable()
    Disposer.register(disposable, Disposable {
      runInEdt {
        runWriteAction {
          Disposer.dispose(artifactTypeDisposable)
        }
      }
    })
    extensionPointName.point.registerExtension(type, artifactTypeDisposable)
  }
}
