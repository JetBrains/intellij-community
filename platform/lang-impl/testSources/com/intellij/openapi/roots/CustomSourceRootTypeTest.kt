// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.module.UnknownSourceRootType
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension
import org.junit.*

class CustomSourceRootTypeTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Rule
  @JvmField
  val tempDirectory = TempDirectory()

  @Rule
  @JvmField
  val disposable = DisposableRule()

  lateinit var module: Module

  @Before
  fun setUp() {
    module = projectModel.createModule()
    JpsModelSerializerExtension.getExtensions()
  }

  @Test
  fun `load unload custom source root`() {
    val srcDir = projectModel.baseProjectDir.newVirtualDirectory("src")
    runWithRegisteredExtension {
      val model = createModifiableModel(module)
      model.addContentEntry(srcDir).addSourceFolder(srcDir, TestCustomSourceRootType.INSTANCE, TestCustomSourceRootProperties("hello"))
      val committed = commitModifiableRootModel(model)
      val sourceFolder = committed.contentEntries.single().sourceFolders.single()
      assertThat(sourceFolder.file).isEqualTo(srcDir)
      assertThat(sourceFolder.rootType).isEqualTo(TestCustomSourceRootType.INSTANCE)
      assertThat((sourceFolder.jpsElement.properties as TestCustomSourceRootProperties).testString).isEqualTo("hello")
    }

    val sourceFolderWithUnknownType = ModuleRootManager.getInstance(module).contentEntries.single().sourceFolders.single()
    assertThat(sourceFolderWithUnknownType.file).isEqualTo(srcDir)
    assertThat(sourceFolderWithUnknownType.rootType).isInstanceOf(UnknownSourceRootType::class.java)

    TestCustomRootModelSerializerExtension.registerTestCustomSourceRootType(tempDirectory.newDirectory(), disposable.disposable)
    val sourceFolder = ModuleRootManager.getInstance(module).contentEntries.single().sourceFolders.single()
    assertThat(sourceFolder.file).isEqualTo(srcDir)
    assertThat(sourceFolder.rootType).isEqualTo(TestCustomSourceRootType.INSTANCE)
    assertThat((sourceFolder.jpsElement.properties as TestCustomSourceRootProperties).testString).isEqualTo("hello")
  }

  @Test
  fun `edit custom root properties`() {
    Assume.assumeTrue("Editing of custom root properties isn't correctly implemented in the current project model",
                      ProjectModelRule.isWorkspaceModelEnabled)
    TestCustomRootModelSerializerExtension.registerTestCustomSourceRootType(tempDirectory.newDirectory(), disposable.disposable)
    val srcDir = projectModel.baseProjectDir.newVirtualDirectory("src")
    val committed = run {
      val model = createModifiableModel(module)
      model.addContentEntry(srcDir).addSourceFolder(srcDir, TestCustomSourceRootType.INSTANCE, TestCustomSourceRootProperties("foo"))
      val sourceFolder = model.contentEntries.single().sourceFolders.single()
      assertThat(sourceFolder.rootType).isEqualTo(TestCustomSourceRootType.INSTANCE)
      assertThat(sourceFolder.customRootProperties).isEqualTo("foo")
      sourceFolder.customRootProperties = "bar"
      assertThat(model.contentEntries.single().sourceFolders.single().customRootProperties).isEqualTo("bar")
      commitModifiableRootModel(model)
    }

    assertThat(committed.contentEntries.single().sourceFolders.single().customRootProperties).isEqualTo("bar")

    val committed2 = run {
      val model = createModifiableModel(module)
      val contentEntry = model.contentEntries.single()
      val sourceFolder = contentEntry.sourceFolders.single()
      assertThat(sourceFolder.customRootProperties).isEqualTo("bar")
      sourceFolder.customRootProperties = "baz"
      assertThat(sourceFolder.customRootProperties).isEqualTo("baz")
      assertThat(contentEntry.sourceFolders.single().customRootProperties).isEqualTo("baz")
      assertThat(model.contentEntries.single().sourceFolders.single().customRootProperties).isEqualTo("baz")
      commitModifiableRootModel(model)
    }

    assertThat(committed2.contentEntries.single().sourceFolders.single().customRootProperties).isEqualTo("baz")
  }

  @Test
  fun `discard changes in custom properties if model is disposed`() {
    TestCustomRootModelSerializerExtension.registerTestCustomSourceRootType(tempDirectory.newDirectory(), disposable.disposable)
    val srcDir = projectModel.baseProjectDir.newVirtualDirectory("src")
    val committed = run {
      val model = createModifiableModel(module)
      model.addContentEntry(srcDir).addSourceFolder(srcDir, TestCustomSourceRootType.INSTANCE, TestCustomSourceRootProperties("foo"))
      commitModifiableRootModel(model)
    }

    assertThat(committed.contentEntries.single().sourceFolders.single().customRootProperties).isEqualTo("foo")

    run {
      val model = createModifiableModel(module)
      model.contentEntries.single().sourceFolders.single().customRootProperties = "bar"
      assertThat(model.contentEntries.single().sourceFolders.single().customRootProperties).isEqualTo("bar")
      model.dispose()
    }

    assertThat(committed.contentEntries.single().sourceFolders.single().customRootProperties).isEqualTo("foo")
  }

  @Test
  fun `test source roots are determined correctly for content roots`() = runWithRegisteredExtension {
    val projectDir = projectModel.baseProjectDir
    val firstContentRoot = projectDir.newVirtualDirectory("src")
    val secondContentRoot = projectDir.newVirtualDirectory("src/main")

    val model = createModifiableModel(module)
    var firstContentEntry = model.addContentEntry(firstContentRoot)
    val sourceFolderForFirstContentEntry = projectDir.newVirtualDirectory("src/main/java")
    firstContentEntry.addSourceFolder(firstContentRoot, TestCustomSourceRootType.INSTANCE, TestCustomSourceRootProperties("src"))
    firstContentEntry.addSourceFolder(sourceFolderForFirstContentEntry, TestCustomSourceRootType.INSTANCE,
                                      TestCustomSourceRootProperties("java"))

    var secondContentEntry = model.addContentEntry(secondContentRoot)
    val sourceFolderForSecondContentEntry = projectDir.newVirtualDirectory("src/main/resources")
    secondContentEntry.addSourceFolder(secondContentRoot, TestCustomSourceRootType.INSTANCE, TestCustomSourceRootProperties("src"))
    secondContentEntry.addSourceFolder(sourceFolderForSecondContentEntry, TestCustomSourceRootType.INSTANCE,
                                       TestCustomSourceRootProperties("java"))
    val committed = commitModifiableRootModel(model)

    assertThat(committed.contentEntries.size).isEqualTo(2)
    committed.contentEntries.sortBy { it.url }
    firstContentEntry = committed.contentEntries[0]
    assertThat(firstContentEntry.url).isEqualTo(firstContentRoot.url)
    var sourceFolders = firstContentEntry.sourceFolders
    sourceFolders.sortBy { it.url }
    assertThat(sourceFolders.size).isEqualTo(2)
    assertThat(sourceFolders[0].url).isEqualTo(firstContentRoot.url)
    assertThat(sourceFolders[1].url).isEqualTo(sourceFolderForFirstContentEntry.url)

    secondContentEntry = committed.contentEntries[1]
    assertThat(secondContentEntry.url).isEqualTo(secondContentRoot.url)
    assertThat(secondContentEntry.sourceFolders.size).isEqualTo(2)
    sourceFolders = secondContentEntry.sourceFolders
    sourceFolders.sortBy { it.url }
    assertThat(sourceFolders.size).isEqualTo(2)
    assertThat(sourceFolders[0].url).isEqualTo(secondContentRoot.url)
    assertThat(sourceFolders[1].url).isEqualTo(sourceFolderForSecondContentEntry.url)
  }

  private var SourceFolder.customRootProperties: String?
    get() = (jpsElement.properties as TestCustomSourceRootProperties).testString
    set(value) {
      (jpsElement.properties as TestCustomSourceRootProperties).testString = value
    }

  private fun runWithRegisteredExtension(action: () -> Unit) {
    val disposable = Disposer.newDisposable()
    TestCustomRootModelSerializerExtension.registerTestCustomSourceRootType(tempDirectory.newDirectory(), disposable)
    try {
      action()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

}