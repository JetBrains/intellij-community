// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.artifacts.workspaceModel

import com.intellij.compiler.artifacts.ArtifactsTestCase
import com.intellij.compiler.artifacts.MockArtifactProperties
import com.intellij.compiler.artifacts.MockArtifactPropertiesProvider
import com.intellij.compiler.artifacts.TestPackagingElementBuilder
import com.intellij.compiler.artifacts.propertybased.*
import com.intellij.concurrency.JobSchedulerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElementType
import com.intellij.packaging.impl.artifacts.InvalidArtifact
import com.intellij.packaging.impl.artifacts.PlainArtifactType
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge.Companion.artifactsMap
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactsTestingState
import com.intellij.packaging.impl.artifacts.workspacemodel.forThisAndFullTree
import com.intellij.packaging.impl.artifacts.workspacemodel.toElement
import com.intellij.packaging.impl.elements.*
import com.intellij.testFramework.JUnit38AssumeSupportRunner
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.bridgeEntities.ArtifactEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ArtifactPropertiesEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ArtifactRootElementEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import junit.framework.TestCase
import org.junit.runner.RunWith
import java.util.concurrent.Callable

@RunWith(JUnit38AssumeSupportRunner::class)
class ArtifactTest : ArtifactsTestCase() {

  override fun tearDown() {
    try {
      ArtifactsTestingState.reset()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test rename artifact via model`() = runWriteAction {
    addArtifact("art")

    val anotherName = "anotherName"

    WorkspaceModel.getInstance(project).updateProjectModel {
      val artifactEntity = it.entities(ArtifactEntity::class.java).single()
      it.modifyEntity(artifactEntity) {
        name = anotherName
      }
    }

    val artifactObject = artifactManager.artifacts.single()
    TestCase.assertEquals(anotherName, artifactObject.name)
  }

  fun `test add artifact via model 2`() = runWriteAction {
    addArtifact("A")
    addArtifact("A2")

    val artifactsCount = artifactManager.artifacts.size
    TestCase.assertEquals(2, artifactsCount)
  }

  fun `test add artifact mix bridge and model`() = runWriteAction {
    val workspaceModel = WorkspaceModel.getInstance(project)

    // Add via model
    workspaceModel.updateProjectModel {
      val root = it.addArtifactRootElementEntity(emptyList(), MySource)
      it.addArtifactEntity("MyName", PlainArtifactType.ID, true, null, root, MySource)
    }

    // Add via bridge
    addArtifact("AnotherName")

    rename(artifactManager.findArtifact("MyName"), "NameThree")

    val artifacts = artifactManager.artifacts
    TestCase.assertEquals(2, artifacts.size)
    TestCase.assertTrue(artifacts.any { it.name == "AnotherName" })
    TestCase.assertTrue(artifacts.any { it.name == "NameThree" })
  }

  fun `test add artifact mix bridge and model rename via model`() = runWriteAction {
    val workspaceModel = WorkspaceModel.getInstance(project)

    // Add via model
    workspaceModel.updateProjectModel {
      val root = it.addArtifactRootElementEntity(emptyList(), MySource)
      it.addArtifactEntity("MyName", PlainArtifactType.ID, true, null, root, MySource)
    }

    // Add via bridge
    addArtifact("AnotherName")

    workspaceModel.updateProjectModel {
      val artifactEntity = it.resolve(ArtifactId("MyName"))!!
      it.modifyEntity(artifactEntity) {
        name = "NameThree"
      }
    }

    val artifacts = artifactManager.artifacts
    TestCase.assertEquals(2, artifacts.size)
    TestCase.assertTrue(artifacts.any { it.name == "AnotherName" })
    TestCase.assertTrue(artifacts.any { it.name == "NameThree" })
  }

  fun `test edit file copy path via model`() {
    val file1 = createTempFile("file1.txt", null)
    val file2 = createTempFile("file2.txt", null)
    addArtifact("a", TestPackagingElementBuilder.root(project).file(file1.systemIndependentPath).build())
    runWriteAction {
      WorkspaceModel.getInstance(project).updateProjectModel { builder ->
        val artifactEntity = builder.entities(ArtifactEntity::class.java).single()
        val elementEntity = artifactEntity.rootElement!!.children.single() as FileCopyPackagingElementEntity
        builder.modifyEntity(FileCopyPackagingElementEntity.Builder::class.java, elementEntity) {
          filePath = VirtualFileUrlManager.getInstance(project).fromPath(file2.systemIndependentPath)
        }
      }
    }
    
    val artifact = artifactManager.artifacts.single()
    val element = artifact.rootElement.children.single() as FileCopyPackagingElement
    assertEquals(file2.systemIndependentPath, element.filePath)
  }

  fun `test edit file renamed output name via model`() {
    val file1 = createTempFile("file1.txt", null)
    addArtifact("a", TestPackagingElementBuilder.root(project).file(file1.systemIndependentPath).build())
    runWriteAction {
      WorkspaceModel.getInstance(project).updateProjectModel { builder ->
        val artifactEntity = builder.entities(ArtifactEntity::class.java).single()
        val elementEntity = artifactEntity.rootElement!!.children.single() as FileCopyPackagingElementEntity
        builder.modifyEntity(FileCopyPackagingElementEntity.Builder::class.java, elementEntity) {
          renamedOutputFileName = "AnotherName"
        }
      }
    }

    val artifact = artifactManager.artifacts.single()
    val element = artifact.rootElement.children.single() as FileCopyPackagingElement
    assertEquals("AnotherName", element.renamedOutputFileName)
  }

  fun `test edit directory name via model`() {
    addArtifact("a", TestPackagingElementBuilder.root(project).dir("MyDirectory").build())
    runWriteAction {
      WorkspaceModel.getInstance(project).updateProjectModel { builder ->
        val artifactEntity = builder.entities(ArtifactEntity::class.java).single()
        val elementEntity = artifactEntity.rootElement!!.children.single() as DirectoryPackagingElementEntity
        builder.modifyEntity(DirectoryPackagingElementEntity.Builder::class.java, elementEntity) {
          this.directoryName = "AnotherName"
        }
      }
    }

    val artifact = artifactManager.artifacts.single()
    val element = artifact.rootElement.children.single() as DirectoryPackagingElement
    assertEquals("AnotherName", element.directoryName)
  }

  fun `test edit archive name via model`() {
    addArtifact("a", TestPackagingElementBuilder.root(project).archive("MyArchive").build())
    runWriteAction {
      WorkspaceModel.getInstance(project).updateProjectModel { builder ->
        val artifactEntity = builder.entities(ArtifactEntity::class.java).single()
        val elementEntity = artifactEntity.rootElement!!.children.single() as ArchivePackagingElementEntity
        builder.modifyEntity(elementEntity) {
          this.fileName = "AnotherName"
        }
      }
    }

    val artifact = artifactManager.artifacts.single()
    val element = artifact.rootElement.children.single() as ArchivePackagingElement
    assertEquals("AnotherName", element.archiveFileName)
  }

  fun `test edit change library via model`() {
    val library = runWriteAction {
      LibraryTablesRegistrar.getInstance().getLibraryTable(project).createLibrary(name)
    }

    addArtifact("a", TestPackagingElementBuilder.root(project).lib(library).build())
    runWriteAction {
      WorkspaceModel.getInstance(project).updateProjectModel { builder ->
        val artifactEntity = builder.entities(ArtifactEntity::class.java).single()
        val elementEntity = artifactEntity.rootElement!!.children.single() as LibraryFilesPackagingElementEntity
        builder.modifyEntity(elementEntity) {
          this.library = LibraryId("123", LibraryTableId.ModuleLibraryTableId(ModuleId("MyModule")))
        }
      }
    }

    val artifact = artifactManager.artifacts.single()
    val element = artifact.rootElement.children.single() as LibraryPackagingElement
    assertEquals("module", element.level)
    assertEquals("123", element.libraryName)
    assertEquals("MyModule", element.moduleName)
  }

  fun `test edit extracted directory via model`() {
    addArtifact("a", TestPackagingElementBuilder.root(project).extractedDir("/test/test", "/path/in/jar").build())
    runWriteAction {
      WorkspaceModel.getInstance(project).updateProjectModel { builder ->
        val artifactEntity = builder.entities(ArtifactEntity::class.java).single()
        val elementEntity = artifactEntity.rootElement!!.children.single() as ExtractedDirectoryPackagingElementEntity
        builder.modifyEntity(ExtractedDirectoryPackagingElementEntity.Builder::class.java, elementEntity) {
          this.pathInArchive = "/another/test"
        }
      }
    }

    val artifact = artifactManager.artifacts.single()
    val element = artifact.rootElement.children.single() as ExtractedDirectoryPackagingElement
    assertEquals("/another/test", element.pathInJar)
  }

  fun `test edit file copy via model`() {
    addArtifact("a", TestPackagingElementBuilder.root(project).file("/test/test").build())
    runWriteAction {
      WorkspaceModel.getInstance(project).updateProjectModel { builder ->
        val artifactEntity = builder.entities(ArtifactEntity::class.java).single()
        val elementEntity = artifactEntity.rootElement!!.children.single() as FileCopyPackagingElementEntity
        builder.modifyEntity(FileCopyPackagingElementEntity.Builder::class.java, elementEntity) {
          this.renamedOutputFileName = "output"
        }
      }
    }

    val artifact = artifactManager.artifacts.single()
    val element = artifact.rootElement.children.single() as FileCopyPackagingElement
    assertEquals("output", element.renamedOutputFileName)
  }

  fun `test add artifact mix bridge and model rename via model same name`() = runWriteAction {
    val workspaceModel = WorkspaceModel.getInstance(project)

    // Add via model
    workspaceModel.updateProjectModel {
      val root = it.addArtifactRootElementEntity(emptyList(), MySource)
      it.addArtifactEntity("MyName", PlainArtifactType.ID, true, null, root, MySource)
    }

    // Add via bridge
    addArtifact("MyName")

    workspaceModel.updateProjectModel {
      val artifactEntity = it.resolve(ArtifactId("MyName"))!!
      it.modifyEntity(artifactEntity) {
        name = "NameThree"
      }
    }

    val artifacts = artifactManager.artifacts
    TestCase.assertEquals(2, artifacts.size)
    TestCase.assertTrue(artifacts.any { it.name == "MyName2" })
    TestCase.assertTrue(artifacts.any { it.name == "NameThree" })
  }

  fun `test dispose modifiable model`() = runWriteAction {
    val workspaceModel = WorkspaceModel.getInstance(project)

    // Add via model
    workspaceModel.updateProjectModel {
      val root = it.addArtifactRootElementEntity(emptyList(), MySource)
      it.addArtifactEntity("MyName", PlainArtifactType.ID, true, null, root, MySource)
    }

    val manager = ArtifactManager.getInstance(project)
    val artifact = manager.artifacts.single()
    val modifiableModel = manager.createModifiableModel()
    val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)

    // Just call this method. It initializes some internal structures
    modifiableArtifact.rootElement

    modifiableModel.dispose()
  }

  fun `test dir with same name`() = runWriteAction {
    val element_0 = DirectoryPackagingElement("Name-15")
    val element_1 = DirectoryPackagingElement("Name-15")
    element_0.addFirstChild(element_1)
    invokeAndWaitIfNeeded {
      runWriteAction {
        ArtifactManager.getInstance(project).addArtifact("Artifact-0", PlainArtifactType.getInstance(), element_0)
      }
    }
    val chosenArtifactEntity = WorkspaceModel.getInstance(project).currentSnapshot.entities(ArtifactEntity::class.java).toList()[0]
    val chosenArtifact = WorkspaceModel.getInstance(project).currentSnapshot.artifactsMap.getDataByEntity(chosenArtifactEntity)!!
    val happyResult_2 = run {
      val modifiableModel = ArtifactManager.getInstance(project).createModifiableModel()
      val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(chosenArtifact)
      val rootElement = modifiableArtifact.rootElement
      val chosenChild = rootElement.children[0]
      rootElement.removeAllChildren()
      val rootElement_2 = modifiableArtifact.rootElement
      modifiableModel.commit()
      return@run Triple(rootElement_2, chosenChild, rootElement)
    }
    val manager = ArtifactManager.getInstance(project)
    val foundArtifact = manager.findArtifact(chosenArtifact.name)!!
    foundArtifact.rootElement.forThisAndFullTree {
      if (it.isEqualTo(happyResult_2.third)) {
        assertTrue((it as CompositePackagingElement<*>).children.none { it.isEqualTo(happyResult_2.second) })
      }
    }
  }

  fun `test another remove`() = runWriteAction {
    val element_0 = DirectoryPackagingElement("Name-19")
    val element_1 = DirectoryPackagingElement("Name-11")
    val element_2 = FileCopyPackagingElement("/Name-20", "Name-7")
    element_1.addFirstChild(element_2)
    element_0.addFirstChild(element_1)
    val element_1_2 = DirectoryPackagingElement("Name-1")
    val element_2_2 = DirectoryPackagingElement("Name-18")
    element_1_2.addFirstChild(element_2_2)
    element_0.addFirstChild(element_1_2)
    val element_1_3 = DirectoryPackagingElement("Name-7")
    val element_2_3 = DirectoryPackagingElement("Name-12")
    val element_3 = DirectoryPackagingElement("Name-18")
    val element_4 = DirectoryPackagingElement("Name-9")
    element_3.addFirstChild(element_4)
    element_2_3.addFirstChild(element_3)
    element_1_3.addFirstChild(element_2_3)
    element_0.addFirstChild(element_1_3)
    val element_1_4 = DirectoryPackagingElement("Name-19")
    val element_2_4 = FileCopyPackagingElement("/Name-8", "Name-13")
    element_1_4.addFirstChild(element_2_4)
    val element_2_5 = DirectoryPackagingElement("Name-9")
    val element_3_2 = DirectoryPackagingElement("Name-20")
    val element_4_2 = DirectoryPackagingElement("Name-19")
    element_3_2.addFirstChild(element_4_2)
    element_2_5.addFirstChild(element_3_2)
    val element_3_3 = FileCopyPackagingElement("/Name-18", "Name-18")
    element_2_5.addFirstChild(element_3_3)
    element_1_4.addFirstChild(element_2_5)
    val element_2_6 = DirectoryPackagingElement("Name-18")
    val element_3_4 = DirectoryPackagingElement("Name-9")
    element_2_6.addFirstChild(element_3_4)
    element_1_4.addFirstChild(element_2_6)
    element_0.addFirstChild(element_1_4)
    run {
      ArtifactManager.getInstance(project).addArtifact("Artifact-0", PlainArtifactType.getInstance(), element_0)
      return@run null
    }
    val chosenArtifactEntity = WorkspaceModel.getInstance(project).currentSnapshot.entities(ArtifactEntity::class.java).toList()[0]
    val chosenArtifact = WorkspaceModel.getInstance(project).currentSnapshot.artifactsMap.getDataByEntity(chosenArtifactEntity)!!
    val happyResult_2 = run {
      val modifiableModel = ArtifactManager.getInstance(project).createModifiableModel()
      val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(chosenArtifact)
      val rootElement = modifiableArtifact.rootElement
      val chosenParent = (rootElement.children[0] as CompositePackagingElement<*>).children[0] as CompositePackagingElement<*>
      val chosenChild = ((rootElement.children[0] as CompositePackagingElement<*>).children[0] as CompositePackagingElement<*>).children[0]
      chosenParent.removeAllChildren()
      val rootElement_2 = modifiableArtifact.rootElement
      modifiableModel.commit()
      return@run Triple(rootElement_2, chosenChild, chosenParent)
    }
    val manager = ArtifactManager.getInstance(project)
    val foundArtifact = manager.findArtifact(chosenArtifact.name)!!
    foundArtifact.rootElement.forThisAndFullTree {
      if (it === happyResult_2.third) {
        assertTrue(it.children.none { it.isEqualTo(happyResult_2.second) })
      }
    }
  }

  fun `test custom element`() = runWriteAction {
    PackagingElementType.EP_NAME.point.registerExtension(MyWorkspacePackagingElementType, this.testRootDisposable)

    val workspaceModel = WorkspaceModel.getInstance(project)
    workspaceModel.updateProjectModel {
      val customElement = it.addCustomPackagingElementEntity("Custom-element", "<CustomPackagingElementState>\n" +
                                                                               "  <option name=\"data\" value=\"Name-2\" />\n" +
                                                                               "</CustomPackagingElementState>", emptyList(), MySource)
      val rootElement = it.addArtifactRootElementEntity(listOf(customElement), MySource)
      it.addArtifactEntity("MyArtifact", PlainArtifactType.ID, false, null, rootElement, MySource)
    }

    val newArtifact = ArtifactManager.getInstance(project).artifacts.single()
    val packagingElement = newArtifact.rootElement.children.single() as MyWorkspacePackagingElement
    TestCase.assertEquals("Name-2", packagingElement.state.data)
  }

  fun `test unknown custom element`() = runWriteAction {
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(testRootDisposable, {})
    val workspaceModel = WorkspaceModel.getInstance(project)
    workspaceModel.updateProjectModel {
      val customElement = it.addCustomPackagingElementEntity("Custom-element", "<CustomPackagingElementState>\n" +
                                                                               "  <option name=\"data\" value=\"Name-2\" />\n" +
                                                                               "</CustomPackagingElementState>", emptyList(), MySource)
      val rootElement = it.addArtifactRootElementEntity(listOf(customElement), MySource)
      it.addArtifactEntity("MyArtifact", PlainArtifactType.ID, false, null, rootElement, MySource)
    }

    val newArtifact = ArtifactManager.getInstance(project).allArtifactsIncludingInvalid.single()
    assertTrue(newArtifact is InvalidArtifact)
  }

  fun `test add root via model and get via bridge`() = runWriteAction {
    val workspaceModel = WorkspaceModel.getInstance(project)
    workspaceModel.updateProjectModel {
      val rootElement = it.addArtifactRootElementEntity(listOf(), MySource)
      it.addArtifactEntity("MyArtifact", PlainArtifactType.ID, false, null, rootElement, MySource)
    }

    val modifiableModel = ArtifactManager.getInstance(project).createModifiableModel()
    val modifiableArtifact = modifiableModel.artifacts.single()
    val element = modifiableArtifact.rootElement
    TestCase.assertEquals(0, element.children.size)
    modifiableModel.commit()
  }

  fun `test custom composite package element`() = runWriteAction {
    PackagingElementType.EP_NAME.point.registerExtension(MyCompositeWorkspacePackagingElementType, this.testRootDisposable)

    val artifactRoot = ArtifactRootElementImpl()
    val element_0 = MyCompositeWorkspacePackagingElement("Name-14", "Name-13")
    val element_1 = DirectoryPackagingElement("Name-17")
    val element_2 = DirectoryPackagingElement("Name-10")
    element_1.addFirstChild(element_2)
    val element_2_2 = DirectoryPackagingElement("Name-1")
    element_1.addFirstChild(element_2_2)
    element_0.addFirstChild(element_1)
    artifactRoot.addFirstChild(element_0)
    invokeAndWaitIfNeeded {
      runWriteAction {
        ArtifactManager.getInstance(project).addArtifact("Artifact-0", PlainArtifactType.getInstance(), artifactRoot)
      }
    }

    val artifact = ArtifactManager.getInstance(project).artifacts.single()
    val rootChildren = artifact.rootElement.children
    TestCase.assertEquals(1, rootChildren.size)
    val customElement = rootChildren.single() as MyCompositeWorkspacePackagingElement
    TestCase.assertEquals("Name-14", customElement.state.data)
    TestCase.assertEquals("Name-13", customElement.state.name)

    val directoryElement = customElement.children.single() as DirectoryPackagingElement
    TestCase.assertEquals("Name-17", directoryElement.directoryName)
  }

  fun `test custom composite package element with adding new child`() = runWriteAction {
    PackagingElementType.EP_NAME.point.registerExtension(MyCompositeWorkspacePackagingElementType, this.testRootDisposable)

    val artifactRoot = ArtifactRootElementImpl()
    val element_0 = MyCompositeWorkspacePackagingElement("Name-14", "Name-13")
    artifactRoot.addFirstChild(element_0)
    invokeAndWaitIfNeeded {
      runWriteAction {
        ArtifactManager.getInstance(project).addArtifact("Artifact-0", PlainArtifactType.getInstance(), artifactRoot)
      }
    }

    invokeAndWaitIfNeeded {
      runWriteAction {
        val artifactManager = ArtifactManager.getInstance(project)
        val artifact = artifactManager.artifacts.single()
        val modifiableModel = artifactManager.createModifiableModel()
        val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
        val element_2_2 = DirectoryPackagingElement("Name-1")
        (modifiableArtifact.rootElement.children.single() as CompositePackagingElement<*>).addFirstChild(element_2_2)
        modifiableModel.commit()
      }
    }

    val artifact = ArtifactManager.getInstance(project).artifacts.single()
    val rootChildren = artifact.rootElement.children
    TestCase.assertEquals(1, rootChildren.size)
    val customElement = rootChildren.single() as MyCompositeWorkspacePackagingElement
    TestCase.assertEquals("Name-14", customElement.state.data)
    TestCase.assertEquals("Name-13", customElement.state.name)

    val directoryElement = customElement.children.single() as DirectoryPackagingElement
    TestCase.assertEquals("Name-1", directoryElement.directoryName)
  }

  fun `test complicated packaging elements structure`() = runWriteAction {
    PackagingElementType.EP_NAME.point.registerExtension(MyCompositeWorkspacePackagingElementType, this.testRootDisposable)

    val artifactRoot = ArtifactRootElementImpl()
    val element_0 = MyCompositeWorkspacePackagingElement("Name-3", "Name-11")
    val element_1 = DirectoryPackagingElement("Name-13")
    element_0.addFirstChild(element_1)
    val element_1_2 = MyCompositeWorkspacePackagingElement("Name-10", "Name-16")
    element_0.addFirstChild(element_1_2)
    val element_1_3 = DirectoryPackagingElement("Name-7")
    element_0.addFirstChild(element_1_3)
    val element_1_4 = DirectoryPackagingElement("Name-12")
    element_0.addFirstChild(element_1_4)
    artifactRoot.addFirstChild(element_0)
    invokeAndWaitIfNeeded {
      runWriteAction {
        ArtifactManager.getInstance(project).addArtifact("Artifact-0", PlainArtifactType.getInstance(), artifactRoot)
      }
    }
    val bridgeArtifact = artifact(project, "Artifact-0")
    (bridgeArtifact.rootElement.children.single() as MyCompositeWorkspacePackagingElement).children
    val artifactEntity = artifactEntity(project, "Artifact-0")
    assertTreesEquals(project, bridgeArtifact.rootElement, artifactEntity.rootElement!!)
  }

  fun `test async artifact initializing`() {
    repeat(1_000) {
      var rootEntity: ArtifactRootElementEntity? = null
      runWriteAction {
        WorkspaceModel.getInstance(project).updateProjectModel {
          rootEntity = it.addArtifactRootElementEntity(emptyList(), MySource)
        }
      }
      val threads = List(10) {
        Callable {
          rootEntity!!.toElement(project, WorkspaceModel.getInstance(project).entityStorage)
        }
      }

      val service = AppExecutorUtil.createBoundedApplicationPoolExecutor("Test executor", JobSchedulerImpl.getCPUCoresCount())
      val res = ConcurrencyUtil.invokeAll(threads, service).map { it.get() }.toSet()
      assertOneElement(res)
    }
  }

  fun `test artifacts with exceptions during initialization`() {
    var exceptionsThrown: List<Int> = emptyList()
    repeat(4) {
      var rootEntity: ArtifactRootElementEntity? = null
      runWriteAction {
        WorkspaceModel.getInstance(project).updateProjectModel {
          rootEntity = it.addArtifactRootElementEntity(emptyList(), MySource)
        }
      }
      ArtifactsTestingState.testLevel = it + 1
      try {
        rootEntity!!.toElement(project, WorkspaceModel.getInstance(project).entityStorage)
      } catch (e: IllegalStateException) {
        if (e.message?.contains("Exception on level") != true) {
          error("Unexpected exception")
        }
      }

      exceptionsThrown = ArtifactsTestingState.exceptionsThrows
    }
    TestCase.assertEquals(listOf(1, 2, 3, 4), exceptionsThrown)
  }

  fun `test async artifacts requesting`() {
    // This test checks that simultaneous requesting of artifacts from different threads won't lead to a multiple instances
    //   of the same artifact.

    repeat(1000) {
      val workspaceModel = WorkspaceModel.getInstance(project)
      val artifacts = workspaceModel.currentSnapshot.entities(ArtifactEntity::class.java).toList()
      runWriteAction {
        workspaceModel.updateProjectModel {
          artifacts.forEach { artifact ->
            it.removeEntity(artifact)
          }
        }
      }

      repeat(10) { counter ->
        runWriteAction {
          workspaceModel.updateProjectModel {
            val rootElementEntity = it.addArtifactRootElementEntity(emptyList(), MySource)
            it.addArtifactEntity("Artifact-$counter", PlainArtifactType.ID, false, null, rootElementEntity, MySource)
          }
        }
      }

      val threads = List(10) {
        Callable {
          runReadAction {
            ArtifactManager.getInstance(project).artifacts
          }
        }
      }

      val service = AppExecutorUtil.createBoundedApplicationPoolExecutor("Test executor", JobSchedulerImpl.getCPUCoresCount())
      val res = ConcurrencyUtil.invokeAll(threads, service).map { it.get().sortedBy { it.name } }
      for (i in res[0].indices) {
        val mainArtifact = res[0][i]
        for (j in 1..res.lastIndex) {
          TestCase.assertSame(mainArtifact, res[j][i])
        }
      }
    }
  }

  fun `test commit and dispose modifiable model`() = runWriteAction {
    val modifiableModel = ArtifactManager.getInstance(project).createModifiableModel()
    val artifact = modifiableModel.addArtifact("MyArtifact", PlainArtifactType.getInstance())
    modifiableModel.commit()

    val modifiableModel2 = ArtifactManager.getInstance(project).createModifiableModel()
    val modifiableArtifact = modifiableModel2.getOrCreateModifiableArtifact(artifact)
    modifiableArtifact.name = "AnotherName"
    modifiableModel2.commit()
    modifiableModel2.dispose()
  }

  fun `test replace root element`() = runWriteAction {
    val modifiableModel = ArtifactManager.getInstance(project).createModifiableModel()
    val artifact = modifiableModel.addArtifact("MyArtifact", PlainArtifactType.getInstance())
    val rootElement = ArtifactRootElementImpl()
    artifact.rootElement = rootElement
    modifiableModel.commit()

    val anotherModifiableModel = ArtifactManager.getInstance(project).createModifiableModel()
    val anotherModifiableArtifact = anotherModifiableModel.getOrCreateModifiableArtifact(artifact)
    val anotherRootElement = ArtifactRootElementImpl()
    anotherModifiableArtifact.rootElement = anotherRootElement
    anotherModifiableModel.commit()

    val rootElements = WorkspaceModel.getInstance(project).currentSnapshot.entities(ArtifactRootElementEntity::class.java).toList()
    assertOneElement(rootElements)
    Unit
  }

  fun `test set property`() = runWriteAction {
    runWithRegisteredExtension(MockArtifactPropertiesProvider(), ArtifactPropertiesProvider.EP_NAME) {
      val modifiableModel = ArtifactManager.getInstance(project).createModifiableModel()
      val artifact = modifiableModel.addArtifact("MyArtifact", PlainArtifactType.getInstance())
      artifact.setProperties(MockArtifactPropertiesProvider.getInstance(), MockArtifactProperties().apply { data = "data" })
      modifiableModel.commit()

      val anotherModifiableModel = ArtifactManager.getInstance(project).createModifiableModel()
      val anotherModifiableArtifact = anotherModifiableModel.getOrCreateModifiableArtifact(artifact)
      anotherModifiableArtifact.setProperties(MockArtifactPropertiesProvider.getInstance(), null)
      anotherModifiableModel.commit()

      val properties = WorkspaceModel.getInstance(project)
        .currentSnapshot
        .entities(ArtifactPropertiesEntity::class.java)
        .filter { it.providerType == MockArtifactPropertiesProvider.getInstance().id }
        .toList()
      assertEmpty(properties)
    }
  }

  fun `test default properties are added`() = runWriteAction {
    runWithRegisteredExtension(MockArtifactPropertiesProvider(), ArtifactPropertiesProvider.EP_NAME) {
      val modifiableModel = ArtifactManager.getInstance(project).createModifiableModel()
      val artifact = modifiableModel.addArtifact("MyArtifact", PlainArtifactType.getInstance())
      modifiableModel.commit()

      val defaultProperties = artifact.getProperties(MockArtifactPropertiesProvider.getInstance())
      TestCase.assertNotNull(defaultProperties)
    }
  }

  fun `test default properties are added with modification`() = runWriteAction {
    runWithRegisteredExtension(MockArtifactPropertiesProvider(), ArtifactPropertiesProvider.EP_NAME) {
      val modifiableModel = ArtifactManager.getInstance(project).createModifiableModel()
      val artifact = modifiableModel.addArtifact("MyArtifact", PlainArtifactType.getInstance())
      modifiableModel.commit()

      val defaultProperties = artifact.getProperties(MockArtifactPropertiesProvider.getInstance())
      TestCase.assertNotNull(defaultProperties)

      val modifiableModel1 = ArtifactManager.getInstance(project).createModifiableModel()
      val modifiableArtifact = modifiableModel1.getOrCreateModifiableArtifact(artifact)
      modifiableArtifact.setProperties(MockArtifactPropertiesProvider.getInstance(), MockArtifactProperties().also { it.data = "123" })
      modifiableModel1.commit()

      val defaultProperties2 = artifact.getProperties(MockArtifactPropertiesProvider.getInstance())
      TestCase.assertNotNull(defaultProperties2)
      assertEquals("123", (defaultProperties2 as MockArtifactProperties).data)
    }
  }

  fun `test work with removed artifact via bridge`() = runWriteAction {
    WorkspaceModel.getInstance(project).updateProjectModel {
      val element = it.addArtifactRootElementEntity(emptyList(), MySource)
      it.addArtifactEntity("MyArtifact", PlainArtifactType.getInstance().id, true, null, element, MySource)
    }
    val artifactEntity = WorkspaceModel.getInstance(project).currentSnapshot.entities(ArtifactEntity::class.java).single()
    val artifactBridge = ArtifactManager.getInstance(project).artifacts[0]

    WorkspaceModel.getInstance(project).updateProjectModel {
      it.removeEntity(artifactEntity.createReference<ArtifactEntity>().resolve(it)!!)
    }

    artifactBridge.rootElement.children
    Unit
  }

  fun `test invalid artifact`() = runWriteAction {
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(testRootDisposable, {})
    val workspaceModel = WorkspaceModel.getInstance(project)
    workspaceModel.updateProjectModel {
      val customElement = it.addCustomPackagingElementEntity("Custom-element", "<CustomPackagingElementState>\n" +
                                                                               "  <option name=\"data\" value=\"Name-2\" />\n" +
                                                                               "</CustomPackagingElementState>", emptyList(), MySource)
      val rootElement = it.addArtifactRootElementEntity(listOf(customElement), MySource)
      it.addArtifactEntity("MyArtifact", PlainArtifactType.ID, false, null, rootElement, MySource)
    }

    val newArtifact = ArtifactManager.getInstance(project).allArtifactsIncludingInvalid.single() as InvalidArtifact

    // Assert empty and assert no exceptions
    // Invalid artifact always has only one root element without children
    assertEmpty(newArtifact.rootElement.children)
  }

  private inline fun <T : Any> runWithRegisteredExtension(extension: T, extensionPoint: ExtensionPointName<T>, action: () -> Unit) {
    val disposable = Disposer.newDisposable()
    registerExtension(extension, extensionPoint, disposable)
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
      runWriteAction {
        Disposer.dispose(artifactTypeDisposable)
      }
    })
    extensionPointName.point.registerExtension(type, artifactTypeDisposable)
  }

  object MySource : EntitySource
}
