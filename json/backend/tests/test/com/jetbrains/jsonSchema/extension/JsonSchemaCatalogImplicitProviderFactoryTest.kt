// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import com.jetbrains.jsonSchema.remote.http.SchemaOrigin
import com.jetbrains.jsonSchema.remote.http.SchemaUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.TreeMap

@TestApplication
class JsonSchemaCatalogImplicitProviderFactoryTest {
  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val project get() = projectFixture.get()

  @AfterEach
  fun tearDown() {
    JsonSchemaMappingsProjectConfiguration.getInstance(project).setState(TreeMap())
    JsonSchemaCatalogProjectConfiguration.getInstance(project).setState(true, true, false, true)
  }

  @Test
  fun testPyprojectTomlGetsImplicitSchema() {
    val file = myFixture.addFileToProject("pyproject.toml", "[project]\nname = \"demo\"\n").virtualFile
    val provider = getImplicitProvider(file)

    assertEquals(PYPROJECT_SCHEMA_URL, provider.remoteSource)
    assertNull(provider.schemaFile)
  }

  @Test
  fun testStandaloneToolConfigGetsImplicitSchema() {
    val file = myFixture.addFileToProject("ruff.toml", "[lint]\nselect = [\"E4\"]\n").virtualFile
    val provider = getImplicitProvider(file)

    assertEquals(RUFF_SCHEMA_URL, provider.remoteSource)
    assertNull(provider.schemaFile)
  }

  @Test
  fun testIgnoredFileDisablesImplicitSchema() {
    val file = myFixture.addFileToProject("pyproject.toml", "[project]\nname = \"demo\"\n").virtualFile

    JsonSchemaMappingsProjectConfiguration.getInstance(project).markAsIgnored(file)
    JsonSchemaService.Impl.get(project).reset()

    assertTrue(getSchemaUrls(file).isEmpty())
  }

  @Test
  fun testImplicitSchemasCanBeDisabledInSettings() {
    val file = myFixture.addFileToProject("pyproject.toml", "[project]\nname = \"demo\"\n").virtualFile

    JsonSchemaCatalogProjectConfiguration.getInstance(project).setState(true, true, false, false)
    JsonSchemaService.Impl.get(project).reset()

    assertTrue(getSchemaUrls(file).isEmpty())
    assertTrue(getSingleSchemaUrls(file).isEmpty())
  }

  @Test
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

    assertEquals(listOf(localSchema.url), getSchemaUrls(file))
    assertEquals(listOf(localSchema.url), getSingleSchemaUrls(file))
  }

  @Test
  fun testCachedFileResolvesBackToImplicitProvider() {
    val file = myFixture.addFileToProject("pyproject.toml", "[project]\nname = \"demo\"\n").virtualFile
    val implicitProvider = getImplicitProvider(file)
    val cachedFile = myFixture.addFileToProject("cached/pyproject.json", "{\"type\":\"object\"}").virtualFile
    markAsCachedContent(cachedFile, PYPROJECT_SCHEMA_URL, PYPROJECT_SCHEMA_REDIRECT_URL)

    val service = JsonSchemaService.Impl.get(project) as JsonSchemaServiceImpl
    val provider = runReadActionBlocking { service.getSchemaProvider(cachedFile) }
    assertNotNull(provider)
    assertEquals(implicitProvider.name, provider?.name)
    assertEquals(PYPROJECT_SCHEMA_URL, provider?.remoteSource)
    assertTrue(runReadActionBlocking { service.isMappedSchema(cachedFile) })
  }

  @Test
  fun testCachedFileResolvesBackToUserMappingWithUnnormalizedUrl() {
    val file = myFixture.addFileToProject("config/settings.json", "{}").virtualFile
    val cachedFile = myFixture.addFileToProject("cached/user.json", "{\"type\":\"object\"}").virtualFile
    markAsCachedContent(cachedFile, "http://json.schemastore.org/user", "https://cdn.example.com/user.json")

    val mapping = UserDefinedJsonSchemaConfiguration(
      "user",
      JsonSchemaVersion.SCHEMA_7,
      "http://json.schemastore.org/user",
      false,
      Collections.singletonList(UserDefinedJsonSchemaConfiguration.Item(file.url, false, false)),
    )
    val state = TreeMap<String, UserDefinedJsonSchemaConfiguration>()
    state[mapping.name] = mapping
    JsonSchemaMappingsProjectConfiguration.getInstance(project).setState(state)
    JsonSchemaService.Impl.get(project).reset()

    val service = JsonSchemaService.Impl.get(project) as JsonSchemaServiceImpl
    val provider = runReadActionBlocking { service.getSchemaProvider(cachedFile) }
    assertEquals("user", provider?.name)
    assertEquals(JsonSchemaVersion.SCHEMA_7, provider?.schemaVersion)
    assertTrue(runReadActionBlocking { service.isMappedSchema(cachedFile) })
  }

  @Test
  fun testIsMappedSchemaDoesNotRecomputeWhenNotAllowed() {
    val cachedFile = myFixture.addFileToProject("cached/pyproject.json", "{\"type\":\"object\"}").virtualFile
    markAsCachedContent(cachedFile, PYPROJECT_SCHEMA_URL, PYPROJECT_SCHEMA_REDIRECT_URL)

    val service = JsonSchemaService.Impl.get(project) as JsonSchemaServiceImpl
    service.reset()

    assertFalse(runReadActionBlocking { service.isMappedSchema(cachedFile, false) })
    assertTrue(runReadActionBlocking { service.isMappedSchema(cachedFile, true) })
    assertTrue(runReadActionBlocking { service.isMappedSchema(cachedFile, false) })

    service.reset()
    assertFalse(runReadActionBlocking { service.isMappedSchema(cachedFile, false) })
  }

  private fun markAsCachedContent(file: VirtualFile, requestUrl: String, retrievalUrl: String) {
    file.putUserData(SchemaOrigin.REQUEST_URL_KEY, SchemaUrl.parse(requestUrl).value)
    file.putUserData(SchemaOrigin.URL_KEY, SchemaUrl.parse(retrievalUrl).value)
  }

  private fun getImplicitProvider(file: VirtualFile): JsonSchemaFileProvider {
    return runReadActionBlocking { JsonSchemaCatalogImplicitProviderFactory().getProviders(project).single { it.isAvailable(file) } }
  }

  private fun getSchemaUrls(file: VirtualFile): List<String> {
    return runReadActionBlocking { JsonSchemaService.Impl.get(project).getSchemaFilesForFile(file) }.map { it.url }
  }

  private fun getSingleSchemaUrls(file: VirtualFile): List<String> {
    val files = runReadActionBlocking { (JsonSchemaService.Impl.get(project) as JsonSchemaServiceImpl).getSchemasForFile(file, true, false) }
    return files.map { it.url }
  }

  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    private const val PYPROJECT_SCHEMA_URL = "https://json.schemastore.org/pyproject.json"
    private const val PYPROJECT_SCHEMA_REDIRECT_URL = "https://www.schemastore.org/pyproject.json"
    private const val RUFF_SCHEMA_URL = "https://www.schemastore.org/ruff.json"
  }
}
