// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote

import com.intellij.json.JsonBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration
import com.jetbrains.jsonSchema.JsonSchemaHeavyTestFixture
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.beans.PropertyChangeListener
import java.util.TreeMap
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JPanel

@TestApplication
class JsonSchemaCatalogNotificationProviderTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val project get() = projectFixture.get()

  private lateinit var catalogManager: JsonSchemaCatalogManager

  @BeforeEach
  fun setUp() {
    myFixture.testDataPath = JsonSchemaHeavyTestFixture.getJsonSchemaTestDataFilePath("schemaStore/")
    catalogManager = configureCatalog()
  }

  @AfterEach
  fun tearDown() {
    JsonSchemaMappingsProjectConfiguration.getInstance(project).setState(TreeMap())
    JsonSchemaCatalogProjectConfiguration.getInstance(project).setState(true, true, false, true)
  }

  @Test
  fun testMatchingCatalogSchemaIsSuggestedInsteadOfApplied(): Unit = timeoutRunBlocking {
    val file = addSchemaStoreOnlyFile()

    assertNotNull(readAction { catalogManager.getSchemaCatalogEntryForFile(file) })
    assertTrue(readAction { JsonSchemaService.Impl.get(project).getSchemaFilesForFile(file) }.isEmpty())

    val provider = JsonSchemaCatalogNotificationProvider()
    val panelFactory = readAction { provider.collectNotificationData(project, file) }
    assertNotNull(panelFactory)
  }

  @Test
  fun testApplyingSuggestedSchemaCreatesExplicitMapping(): Unit = timeoutRunBlocking {
    val file = addSchemaStoreOnlyFile()
    val provider = JsonSchemaCatalogNotificationProvider()

    val panelFactory = readAction { provider.collectNotificationData(project, file) }
    assertNotNull(panelFactory)

    val panel = createPanel(file, panelFactory!!)
    clickAction(panel, JsonBundle.message("schema.catalog.suggestion.apply"))

    assertNotNull(JsonSchemaMappingsProjectConfiguration.getInstance(project).findMappingForFile(file))
    assertNull(readAction { provider.collectNotificationData(project, file) })
  }

  @Test
  fun testDisableCatalogSuggestionTurnsOffCatalog(): Unit = timeoutRunBlocking {
    val file = addSchemaStoreOnlyFile()
    val provider = JsonSchemaCatalogNotificationProvider()

    val panelFactory = readAction { provider.collectNotificationData(project, file) }
    assertNotNull(panelFactory)

    val panel = createPanel(file, panelFactory!!)
    clickAction(panel, JsonBundle.message("schema.catalog.suggestion.disable"))

    assertFalse(JsonSchemaCatalogProjectConfiguration.getInstance(project).isCatalogEnabled)
    assertNull(readAction { provider.collectNotificationData(project, file) })
  }

  private suspend fun addSchemaStoreOnlyFile(): VirtualFile {
    val service = JsonSchemaService.Impl.get(project)
    val candidates = arrayOf(
      arrayOf("jenkins-x.yml", "kind: pipeline"),
      arrayOf(".circleci/config.yml", "version: 2.1"),
      arrayOf(".github/workflows/nodejs.yml", "name: CI"),
      arrayOf("package.json", "{\"name\":\"demo\"}"),
    )

    for (candidate in candidates) {
      val file = myFixture.addFileToProject(candidate[0], candidate[1]).virtualFile
      if (readAction { service.isApplicableToFile(file) && catalogManager.getSchemaCatalogEntryForFile(file) != null && service.getSchemaFilesForFile(file).isEmpty() }) {
        return file
      }
    }

    fail("Could not find a SchemaStore-only test fixture for the current product")
  }

  private suspend fun clickAction(panel: EditorNotificationPanel, linkText: String) {
    val links = UIUtil.findComponentsOfType(panel, HyperlinkLabel::class.java)
    val link = links.firstOrNull { it.text == linkText }
    assertNotNull(link, "Could not find action '$linkText'")
    withContext(Dispatchers.EDT) { link!!.doClick() }
  }

  private suspend fun createPanel(
    file: VirtualFile,
    panelFactory: Function<in FileEditor, out JComponent?>,
  ): EditorNotificationPanel {
    return withContext(Dispatchers.EDT) {
      panelFactory.apply(TestFileEditor(file)) as EditorNotificationPanel
    }
  }

  private fun configureCatalog(): JsonSchemaCatalogManager {
    val manager = JsonSchemaService.Impl.get(project).catalogManager
    val path = JsonSchemaHeavyTestFixture.getJsonSchemaTestDataFilePath("schemaStore/catalog.json")
    val catalogFile = StandardFileSystems.local().findFileByPath(path)
    assertNotNull(catalogFile)
    manager.registerTestSchemaStoreFile(catalogFile!!, myFixture.testRootDisposable)
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
