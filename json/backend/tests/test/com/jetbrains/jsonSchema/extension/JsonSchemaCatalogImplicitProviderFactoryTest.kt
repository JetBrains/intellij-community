// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import java.util.Collections
import java.util.TreeMap

class JsonSchemaCatalogImplicitProviderFactoryTest : BasePlatformTestCase() {
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

  fun testPyprojectTomlGetsImplicitSchema() {
    val file = myFixture.addFileToProject("pyproject.toml", "[project]\nname = \"demo\"\n").virtualFile

    assertEquals(listOf(PYPROJECT_SCHEMA_URL), getSchemaUrls(file))
    assertEquals(listOf(PYPROJECT_SCHEMA_URL), getSingleSchemaUrls(file))
  }

  fun testStandaloneToolConfigGetsImplicitSchema() {
    val file = myFixture.addFileToProject("ruff.toml", "[lint]\nselect = [\"E4\"]\n").virtualFile

    assertEquals(listOf(RUFF_SCHEMA_URL), getSchemaUrls(file))
    assertEquals(listOf(RUFF_SCHEMA_URL), getSingleSchemaUrls(file))
  }

  fun testIgnoredFileDisablesImplicitSchema() {
    val file = myFixture.addFileToProject("pyproject.toml", "[project]\nname = \"demo\"\n").virtualFile

    JsonSchemaMappingsProjectConfiguration.getInstance(project).markAsIgnored(file)
    JsonSchemaService.Impl.get(project).reset()

    assertEmpty(getSchemaUrls(file))
  }

  fun testImplicitSchemasCanBeDisabledInSettings() {
    val file = myFixture.addFileToProject("pyproject.toml", "[project]\nname = \"demo\"\n").virtualFile

    JsonSchemaCatalogProjectConfiguration.getInstance(project).setState(true, true, false, false)
    JsonSchemaService.Impl.get(project).reset()

    assertEmpty(getSchemaUrls(file))
    assertEmpty(getSingleSchemaUrls(file))
  }

  fun testUserMappingOverridesImplicitSchemaInSingleMode() {
    val file = myFixture.addFileToProject("pyproject.toml", "[project]\nname = \"demo\"\n").virtualFile
    val localSchema = myFixture.addFileToProject("schemas/local.json", "{\"type\":\"object\"}").virtualFile

    val mapping = UserDefinedJsonSchemaConfiguration(
      "local",
      JsonSchemaVersion.SCHEMA_4,
      localSchema.url,
      false,
      Collections.singletonList(UserDefinedJsonSchemaConfiguration.Item(file.url, false, false)),
    )
    val state = TreeMap<String, UserDefinedJsonSchemaConfiguration>()
    state[mapping.name] = mapping
    JsonSchemaMappingsProjectConfiguration.getInstance(project).setState(state)
    JsonSchemaService.Impl.get(project).reset()

    assertContainsElements(getSchemaUrls(file), localSchema.url, PYPROJECT_SCHEMA_URL)
    assertEquals(listOf(localSchema.url), getSingleSchemaUrls(file))
  }

  private fun getSchemaUrls(file: VirtualFile): List<String> {
    return JsonSchemaService.Impl.get(project).getSchemaFilesForFile(file).map(VirtualFile::getUrl)
  }

  private fun getSingleSchemaUrls(file: VirtualFile): List<String> {
    val files = (JsonSchemaService.Impl.get(project) as JsonSchemaServiceImpl).getSchemasForFile(file, true, false)
    return files.map(VirtualFile::getUrl)
  }

  companion object {
    private const val PYPROJECT_SCHEMA_URL = "https://json.schemastore.org/pyproject.json"
    private const val RUFF_SCHEMA_URL = "https://www.schemastore.org/ruff.json"
  }
}
