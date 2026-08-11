// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote

import com.intellij.json.JsonBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.runInEdtAndWait as runInEdtAndWaitKt
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration
import com.jetbrains.jsonSchema.JsonSchemaHeavyAbstractTest
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import java.beans.PropertyChangeListener
import java.util.TreeMap
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JPanel

class JsonSchemaCatalogNotificationProviderTest : BasePlatformTestCase() {
  private lateinit var catalogManager: JsonSchemaCatalogManager

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    catalogManager = configureCatalog()
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      JsonSchemaMappingsProjectConfiguration.getInstance(project).setState(TreeMap())
      JsonSchemaCatalogProjectConfiguration.getInstance(project).setState(true, true, false, true)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  override fun getTestDataPath(): String = JsonSchemaHeavyAbstractTest.getJsonSchemaTestDataFilePath("schemaStore/")

  fun testMatchingCatalogSchemaIsSuggestedInsteadOfApplied() {
    val file = addSchemaStoreOnlyFile()

    assertNotNull(catalogManager.getSchemaFileForFile(file))
    assertEmpty(JsonSchemaService.Impl.get(project).getSchemaFilesForFile(file))

    val provider = JsonSchemaCatalogNotificationProvider()
    val panelFactory = provider.collectNotificationData(project, file)
    assertNotNull(panelFactory)
  }

  fun testApplyingSuggestedSchemaCreatesExplicitMapping() {
    val file = addSchemaStoreOnlyFile()
    val provider = JsonSchemaCatalogNotificationProvider()

    val panelFactory = provider.collectNotificationData(project, file)
    assertNotNull(panelFactory)

    val panel = createPanel(file, panelFactory!!)
    clickAction(panel, JsonBundle.message("schema.catalog.suggestion.apply"))

    assertNotNull(JsonSchemaMappingsProjectConfiguration.getInstance(project).findMappingForFile(file))
    assertNotEmpty(JsonSchemaService.Impl.get(project).getSchemaFilesForFile(file))
    assertNull(provider.collectNotificationData(project, file))
  }

  fun testDisableCatalogSuggestionTurnsOffCatalog() {
    val file = addSchemaStoreOnlyFile()
    val provider = JsonSchemaCatalogNotificationProvider()

    val panelFactory = provider.collectNotificationData(project, file)
    assertNotNull(panelFactory)

    val panel = createPanel(file, panelFactory!!)
    clickAction(panel, JsonBundle.message("schema.catalog.suggestion.disable"))

    assertFalse(JsonSchemaCatalogProjectConfiguration.getInstance(project).isCatalogEnabled)
    assertNull(provider.collectNotificationData(project, file))
  }

  private fun addSchemaStoreOnlyFile(): VirtualFile {
    val service = JsonSchemaService.Impl.get(project)
    val candidates = arrayOf(
      arrayOf("jenkins-x.yml", "kind: pipeline"),
      arrayOf(".circleci/config.yml", "version: 2.1"),
      arrayOf(".github/workflows/nodejs.yml", "name: CI"),
      arrayOf("package.json", "{\"name\":\"demo\"}"),
    )

    for (candidate in candidates) {
      val file = myFixture.addFileToProject(candidate[0], candidate[1]).virtualFile
      if (service.isApplicableToFile(file) && catalogManager.getSchemaFileForFile(file) != null && service.getSchemaFilesForFile(file).isEmpty()) {
        return file
      }
    }

    fail("Could not find a SchemaStore-only test fixture for the current product")
    error("unreachable")
  }

  private fun clickAction(panel: EditorNotificationPanel, linkText: String) {
    val links = UIUtil.findComponentsOfType(panel, HyperlinkLabel::class.java)
    val link = links.firstOrNull { it.text == linkText }
    assertNotNull("Could not find action '$linkText'", link)
    runInEdtAndWaitKt { link!!.doClick() }
  }

  private fun createPanel(
    file: VirtualFile,
    panelFactory: Function<in FileEditor, out JComponent?>,
  ): EditorNotificationPanel {
    lateinit var panel: EditorNotificationPanel
    runInEdtAndWaitKt {
      panel = panelFactory.apply(TestFileEditor(file)) as EditorNotificationPanel
    }
    return panel
  }

  private fun configureCatalog(): JsonSchemaCatalogManager {
    val manager = JsonSchemaService.Impl.get(project).catalogManager
    val path = JsonSchemaHeavyAbstractTest.getJsonSchemaTestDataFilePath("schemaStore/catalog.json")
    val catalogFile = LocalFileSystem.getInstance().findFileByPath(path)
    assertNotNull(catalogFile)
    manager.registerTestSchemaStoreFile(catalogFile!!, testRootDisposable)
    return manager
  }

  private class TestFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val component = JPanel()

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent = component

    override fun getName(): String = "JsonSchemaCatalogNotificationProviderTest"

    override fun setState(state: FileEditorState) {
    }

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun getFile(): VirtualFile = file

    override fun dispose() {
    }
  }
}
