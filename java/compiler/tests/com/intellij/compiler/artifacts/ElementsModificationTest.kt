// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.artifacts

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.packaging.artifacts.Artifact
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.elements.PackagingElementFactory
import com.intellij.packaging.impl.artifacts.PlainArtifactType
import com.intellij.packaging.impl.elements.ExtractedDirectoryPackagingElement
import com.intellij.packaging.impl.elements.FileCopyPackagingElement
import com.intellij.packaging.impl.elements.LibraryPackagingElement
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File

class ElementsModificationTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test
  fun `create library artifact`() {
    val project = projectModel.project

    val artifactManager = ArtifactManager.getInstance(project)
    WriteAction.runAndWait<RuntimeException> {
      val artifact = artifactManager.addArtifact("Artifact", PlainArtifactType.getInstance(),
                                                 PackagingElementFactory.getInstance().createArtifactRootElement())
      val modifiableModel = artifactManager.createModifiableModel()
      val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      modifiableArtifact.rootElement.addOrFindChild(PackagingElementFactory.getInstance().createLibraryFiles("One", "project", null))
      modifiableModel.commit()
    }

    PlatformTestUtil.saveProject(project)

    assertArtifactFileTextEquals("""
      |<component name="ArtifactManager">
      |  <artifact name="Artifact">
      |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
      |    <root id="root">
      |      <element id="library" level="project" name="One" />
      |    </root>
      |  </artifact>
      |</component>""".trimMargin())
  }

  @Test
  fun `rename library artifact`() {
    val project = projectModel.project

    val artifactManager = ArtifactManager.getInstance(project)
    val artifact = WriteAction.computeAndWait<Artifact, Throwable> {
      val rootElement = PackagingElementFactory.getInstance().createArtifactRootElement()
      rootElement.addOrFindChild(PackagingElementFactory.getInstance().createLibraryFiles("One", "project", null))
      artifactManager.addArtifact("Artifact", PlainArtifactType.getInstance(), rootElement)
    }

    PlatformTestUtil.saveProject(project)
    WriteAction.runAndWait<RuntimeException> {
      val modifiableModel = artifactManager.createModifiableModel()
      val mutableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      val libraryElement = mutableArtifact.rootElement.children[0] as LibraryPackagingElement
      libraryElement.libraryName = "AnotherName"
      modifiableModel.commit()
    }

    PlatformTestUtil.saveProject(project)

    assertArtifactFileTextEquals("""
      |<component name="ArtifactManager">
      |  <artifact name="Artifact">
      |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
      |    <root id="root">
      |      <element id="library" level="project" name="AnotherName" />
      |    </root>
      |  </artifact>
      |</component>""".trimMargin())
  }

  @Test
  fun `change level of library artifact`() {
    val project = projectModel.project

    val artifactManager = ArtifactManager.getInstance(project)
    val artifact = WriteAction.computeAndWait<Artifact, Throwable> {
      val rootElement = PackagingElementFactory.getInstance().createArtifactRootElement()
      rootElement.addOrFindChild(PackagingElementFactory.getInstance().createLibraryFiles("One", "project", null))
      artifactManager.addArtifact("Artifact", PlainArtifactType.getInstance(), rootElement)
    }

    PlatformTestUtil.saveProject(project)
    WriteAction.runAndWait<RuntimeException> {
      val modifiableModel = artifactManager.createModifiableModel()
      val mutableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      val libraryElement = mutableArtifact.rootElement.children[0] as LibraryPackagingElement
      libraryElement.level = "Custom"
      modifiableModel.commit()
    }

    PlatformTestUtil.saveProject(project)

    assertArtifactFileTextEquals("""
      |<component name="ArtifactManager">
      |  <artifact name="Artifact">
      |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
      |    <root id="root">
      |      <element id="library" level="Custom" name="One" />
      |    </root>
      |  </artifact>
      |</component>""".trimMargin())
  }

  @Test
  fun `change module name of library artifact`() {
    val project = projectModel.project

    val artifactManager = ArtifactManager.getInstance(project)
    val artifact = WriteAction.computeAndWait<Artifact, Throwable> {
      val rootElement = PackagingElementFactory.getInstance().createArtifactRootElement()
      rootElement.addOrFindChild(PackagingElementFactory.getInstance().createLibraryFiles("One", "module", "myModule"))
      artifactManager.addArtifact("Artifact", PlainArtifactType.getInstance(), rootElement)
    }

    PlatformTestUtil.saveProject(project)
    WriteAction.runAndWait<RuntimeException> {
      val modifiableModel = artifactManager.createModifiableModel()
      val mutableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      val libraryElement = mutableArtifact.rootElement.children[0] as LibraryPackagingElement
      libraryElement.moduleName = "AnotherModuleName"
      modifiableModel.commit()
    }

    PlatformTestUtil.saveProject(project)

    assertArtifactFileTextEquals("""
      |<component name="ArtifactManager">
      |  <artifact name="Artifact">
      |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
      |    <root id="root">
      |      <element id="library" level="module" name="One" module-name="AnotherModuleName" />
      |    </root>
      |  </artifact>
      |</component>""".trimMargin())
  }

  @Test
  fun `change path in jar for extracted directory`() {
    val project = projectModel.project

    val artifactManager = ArtifactManager.getInstance(project)
    val artifact = WriteAction.computeAndWait<Artifact, Throwable> {
      val rootElement = PackagingElementFactory.getInstance().createArtifactRootElement()

      val file = VfsTestUtil.createFile(projectModel.baseProjectDir.virtualFileRoot, "MyPath.jar", "")
      val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(file)!!
      rootElement.addOrFindChild(PackagingElementFactory.getInstance().createExtractedDirectory(jarRoot))
      artifactManager.addArtifact("Artifact", PlainArtifactType.getInstance(), rootElement)
    }

    PlatformTestUtil.saveProject(project)
    WriteAction.runAndWait<RuntimeException> {
      val modifiableModel = artifactManager.createModifiableModel()
      val mutableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      val element = mutableArtifact.rootElement.children[0] as ExtractedDirectoryPackagingElement
      element.pathInJar = "AnotherPath"
      modifiableModel.commit()
    }

    PlatformTestUtil.saveProject(project)

    assertArtifactFileTextEquals("""
      |<component name="ArtifactManager">
      |  <artifact name="Artifact">
      |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
      |    <root id="root">
      |      <element id="extracted-dir" path="${'$'}PROJECT_DIR${'$'}/MyPath.jar" path-in-jar="AnotherPath" />
      |    </root>
      |  </artifact>
      |</component>""".trimMargin())
  }

  @Test
  fun `update file copy packaging element`() {
    val project = projectModel.project

    val artifactManager = ArtifactManager.getInstance(project)
    val artifact = WriteAction.computeAndWait<Artifact, Throwable> {
      val rootElement = PackagingElementFactory.getInstance().createArtifactRootElement()

      rootElement.addOrFindChild(PackagingElementFactory.getInstance().createFileCopy("myPath", null))
      artifactManager.addArtifact("Artifact", PlainArtifactType.getInstance(), rootElement)
    }

    PlatformTestUtil.saveProject(project)
    WriteAction.runAndWait<RuntimeException> {
      val modifiableModel = artifactManager.createModifiableModel()
      val mutableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      val element = mutableArtifact.rootElement.children[0] as FileCopyPackagingElement
      element.renamedOutputFileName = "Rename"
      modifiableModel.commit()
    }

    PlatformTestUtil.saveProject(project)

    assertArtifactFileTextEquals("""
      |<component name="ArtifactManager">
      |  <artifact name="Artifact">
      |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
      |    <root id="root">
      |      <element id="file-copy" path="myPath" output-file-name="Rename" />
      |    </root>
      |  </artifact>
      |</component>""".trimMargin())
  }

  @Test
  fun `update file copy packaging element rename`() {
    val project = projectModel.project

    val artifactManager = ArtifactManager.getInstance(project)
    val artifact = WriteAction.computeAndWait<Artifact, Throwable> {
      val rootElement = PackagingElementFactory.getInstance().createArtifactRootElement()

      rootElement.addOrFindChild(PackagingElementFactory.getInstance().createFileCopy("myPath", null))
      artifactManager.addArtifact("Artifact", PlainArtifactType.getInstance(), rootElement)
    }

    PlatformTestUtil.saveProject(project)
    WriteAction.runAndWait<RuntimeException> {
      val modifiableModel = artifactManager.createModifiableModel()
      val mutableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      val element = mutableArtifact.rootElement.children[0] as FileCopyPackagingElement
      element.rename("Rename")
      modifiableModel.commit()
    }

    PlatformTestUtil.saveProject(project)

    assertArtifactFileTextEquals("""
      |<component name="ArtifactManager">
      |  <artifact name="Artifact">
      |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
      |    <root id="root">
      |      <element id="file-copy" path="myPath" output-file-name="Rename" />
      |    </root>
      |  </artifact>
      |</component>""".trimMargin())
  }

  @Test
  fun `update file copy packaging element set file path`() {
    val project = projectModel.project

    val artifactManager = ArtifactManager.getInstance(project)
    val artifact = WriteAction.computeAndWait<Artifact, Throwable> {
      val rootElement = PackagingElementFactory.getInstance().createArtifactRootElement()

      rootElement.addOrFindChild(PackagingElementFactory.getInstance().createFileCopy("myPath", null))
      artifactManager.addArtifact("Artifact", PlainArtifactType.getInstance(), rootElement)
    }

    PlatformTestUtil.saveProject(project)
    WriteAction.runAndWait<RuntimeException> {
      val modifiableModel = artifactManager.createModifiableModel()
      val mutableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      val element = mutableArtifact.rootElement.children[0] as FileCopyPackagingElement
      element.filePath = "AnotherFilePath"
      modifiableModel.commit()
    }

    PlatformTestUtil.saveProject(project)

    assertArtifactFileTextEquals("""
      |<component name="ArtifactManager">
      |  <artifact name="Artifact">
      |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
      |    <root id="root">
      |      <element id="file-copy" path="AnotherFilePath" />
      |    </root>
      |  </artifact>
      |</component>""".trimMargin())
  }

  @Test
  fun `modification double modification`() {
    val project = projectModel.project

    val artifactManager = ArtifactManager.getInstance(project)
    val artifact = WriteAction.computeAndWait<Artifact, Throwable> {
      val rootElement = PackagingElementFactory.getInstance().createArtifactRootElement()
      rootElement.addOrFindChild(PackagingElementFactory.getInstance().createLibraryFiles("One", "project", null))
      artifactManager.addArtifact("Artifact", PlainArtifactType.getInstance(), rootElement)
    }

    PlatformTestUtil.saveProject(project)
    WriteAction.runAndWait<RuntimeException> {
      val modifiableModel = artifactManager.createModifiableModel()
      val mutableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      val libraryElement = mutableArtifact.rootElement.children[0] as LibraryPackagingElement
      libraryElement.libraryName = "Two"
      modifiableModel.commit()
    }

    PlatformTestUtil.saveProject(project)
    assertArtifactFileTextEquals("""
      |<component name="ArtifactManager">
      |  <artifact name="Artifact">
      |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
      |    <root id="root">
      |      <element id="library" level="project" name="Two" />
      |    </root>
      |  </artifact>
      |</component>""".trimMargin())

    PlatformTestUtil.saveProject(project)
    WriteAction.runAndWait<RuntimeException> {
      val modifiableModel = artifactManager.createModifiableModel()
      val mutableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      val libraryElement = mutableArtifact.rootElement.children[0] as LibraryPackagingElement
      libraryElement.libraryName = "Three"
      modifiableModel.commit()
    }

    PlatformTestUtil.saveProject(project)

    assertArtifactFileTextEquals("""
      |<component name="ArtifactManager">
      |  <artifact name="Artifact">
      |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
      |    <root id="root">
      |      <element id="library" level="project" name="Three" />
      |    </root>
      |  </artifact>
      |</component>""".trimMargin())
  }

  @Test
  fun `modification with dispose`() {
    val project = projectModel.project

    val artifactManager = ArtifactManager.getInstance(project)
    val artifact = WriteAction.computeAndWait<Artifact, Throwable> {
      val rootElement = PackagingElementFactory.getInstance().createArtifactRootElement()
      rootElement.addOrFindChild(PackagingElementFactory.getInstance().createLibraryFiles("One", "project", null))
      artifactManager.addArtifact("Artifact", PlainArtifactType.getInstance(), rootElement)
    }

    PlatformTestUtil.saveProject(project)
    WriteAction.runAndWait<RuntimeException> {
      val modifiableModel = artifactManager.createModifiableModel()
      val mutableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      val libraryElement = mutableArtifact.rootElement.children[0] as LibraryPackagingElement
      libraryElement.libraryName = "Two"
      modifiableModel.dispose()
    }

    PlatformTestUtil.saveProject(project)
    assertArtifactFileTextEquals("""
      |<component name="ArtifactManager">
      |  <artifact name="Artifact">
      |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
      |    <root id="root">
      |      <element id="library" level="project" name="One" />
      |    </root>
      |  </artifact>
      |</component>""".trimMargin())

    PlatformTestUtil.saveProject(project)
    WriteAction.runAndWait<RuntimeException> {
      val modifiableModel = artifactManager.createModifiableModel()
      val mutableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      val libraryElement = mutableArtifact.rootElement.children[0] as LibraryPackagingElement
      libraryElement.libraryName = "Three"
      modifiableModel.commit()
    }

    PlatformTestUtil.saveProject(project)

    assertArtifactFileTextEquals("""
      |<component name="ArtifactManager">
      |  <artifact name="Artifact">
      |    <output-path>${'$'}PROJECT_DIR${'$'}/out/artifacts/Artifact</output-path>
      |    <root id="root">
      |      <element id="library" level="project" name="Three" />
      |    </root>
      |  </artifact>
      |</component>""".trimMargin())
  }

  private fun assertArtifactFileTextEquals(expectedText: String) {
    assertEquals(StringUtil.convertLineSeparators(expectedText),
                 StringUtil.convertLineSeparators(File(projectModel.baseProjectDir.root, ".idea/artifacts/Artifact.xml").readText()))
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}
