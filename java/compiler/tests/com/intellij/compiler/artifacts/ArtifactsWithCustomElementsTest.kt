// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.artifacts

import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.packaging.artifacts.Artifact
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.elements.PackagingElementFactory
import com.intellij.packaging.elements.PackagingElementType
import com.intellij.packaging.impl.artifacts.PlainArtifactType
import com.intellij.packaging.ui.ArtifactEditorContext
import com.intellij.packaging.ui.PackagingElementPresentation
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.xmlb.annotations.Attribute
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.function.Supplier

class ArtifactsWithCustomElementsTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test
  fun `create custom artifact`() {
    val project = projectModel.project

    val artifactManager = ArtifactManager.getInstance(project)
    WriteAction.runAndWait<RuntimeException> {
      val artifact = artifactManager.addArtifact("Artifact", PlainArtifactType.getInstance(),
                                                 PackagingElementFactory.getInstance().createArtifactRootElement())
      val modifiableModel = artifactManager.createModifiableModel()
      val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      modifiableArtifact.rootElement.addOrFindChild(MyPackagingElement("MyData"))
      modifiableModel.commit()
    }

    PlatformTestUtil.saveProject(project)

    assertArtifactFileTextEquals(
      """
        |<component name="ArtifactManager">
        |  <artifact name="Artifact">
        |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
        |    <root id="root">
        |      <element id="MyElement" data="MyData" />
        |    </root>
        |  </artifact>
        |</component>""".trimMargin())
  }

  @Test
  fun `modify custom artifact`() {
    val project = projectModel.project

    val artifactManager = ArtifactManager.getInstance(project)
    WriteAction.runAndWait<RuntimeException> {
      val artifact = artifactManager.addArtifact("Artifact", PlainArtifactType.getInstance(),
                                                 PackagingElementFactory.getInstance().createArtifactRootElement())
      val modifiableModel = artifactManager.createModifiableModel()
      val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      modifiableArtifact.rootElement.addOrFindChild(MyPackagingElement("MyData"))
      modifiableModel.commit()

      val modifiableModel2 = artifactManager.createModifiableModel()
      val modifiableArtifact2 = modifiableModel2.getOrCreateModifiableArtifact(artifact)
      val packagingElement = modifiableArtifact2.rootElement.children[0] as MyPackagingElement
      packagingElement.data = "AnotherData"
      modifiableModel2.commit()
    }

    PlatformTestUtil.saveProject(project)

    assertArtifactFileTextEquals(
      """
        |<component name="ArtifactManager">
        |  <artifact name="Artifact">
        |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
        |    <root id="root">
        |      <element id="MyElement" data="AnotherData" />
        |    </root>
        |  </artifact>
        |</component>""".trimMargin())
  }

  private fun assertArtifactFileTextEquals(expectedText: String) {
    Assert.assertEquals(StringUtil.convertLineSeparators(expectedText),
                        StringUtil.convertLineSeparators(File(projectModel.baseProjectDir.root, ".idea/artifacts/Artifact.xml").readText()))
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}

object MyPackagingElementType : PackagingElementType<MyPackagingElement>("MyElement", Supplier { "My element" }) {
  override fun canCreate(context: ArtifactEditorContext, artifact: Artifact): Boolean {
    return true
  }

  override fun chooseAndCreate(context: ArtifactEditorContext,
                               artifact: Artifact,
                               parent: CompositePackagingElement<*>): List<PackagingElement<*>> {
    return listOf(MyPackagingElement())
  }

  override fun createEmpty(project: Project): MyPackagingElement {
    return MyPackagingElement()
  }
}

class MyPackagingElement(_data: String?) : PackagingElement<MyPackagingElement>(MyPackagingElementType) {

  constructor() : this(null)

  @Attribute("data")
  var data: String? = _data

  override fun createPresentation(context: ArtifactEditorContext): PackagingElementPresentation {
    return object : PackagingElementPresentation() {
      override fun getPresentableName(): String = "My element"

      override fun render(presentationData: PresentationData,
                          mainAttributes: SimpleTextAttributes?,
                          commentAttributes: SimpleTextAttributes?) {
      }

      override fun getWeight(): Int = 0
    }
  }

  override fun isEqualTo(element: PackagingElement<*>): Boolean {
    return element is MyPackagingElement && element.data == this.data
  }

  override fun getState(): MyPackagingElement {
    return this
  }

  override fun loadState(state: MyPackagingElement) {
    this.data = state.data
  }
}
