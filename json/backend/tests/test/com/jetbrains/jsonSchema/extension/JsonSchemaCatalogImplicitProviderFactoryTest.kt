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
import com.jetbrains.jsonSchema.remote.http.SchemaOrigin
import com.jetbrains.jsonSchema.remote.http.SchemaUrl
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
    val provider = getImplicitProvider(file)

    assertEquals(PYPROJECT_SCHEMA_URL, provider.remoteSource)
    assertNull(provider.schemaFile)
  }

  fun testStandaloneToolConfigGetsImplicitSchema() {
    val file = myFixture.addFileToProject("ruff.toml", "[lint]\nselect = [\"E4\"]\n").virtualFile
    val provider = getImplicitProvider(file)

    assertEquals(RUFF_SCHEMA_URL, provider.remoteSource)
    assertNull(provider.schemaFile)
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

    assertEquals(listOf(localSchema.url), getSchemaUrls(file))
    assertEquals(listOf(localSchema.url), getSingleSchemaUrls(file))
  }

  fun testCachedFileResolvesBackToImplicitProvider() {
    val file = myFixture.addFileToProject("pyproject.toml", "[project]\nname = \"demo\"\n").virtualFile
    val implicitProvider = getImplicitProvider(file)
    val cachedFile = myFixture.addFileToProject("cached/pyproject.json", "{\"type\":\"object\"}").virtualFile
    markAsCachedContent(cachedFile, PYPROJECT_SCHEMA_URL, PYPROJECT_SCHEMA_REDIRECT_URL)

    val service = JsonSchemaService.Impl.get(project) as JsonSchemaServiceImpl
    val provider = service.getSchemaProvider(cachedFile)
    assertNotNull(provider)
    assertEquals(implicitProvider.name, provider?.name)
    assertEquals(PYPROJECT_SCHEMA_URL, provider?.remoteSource)
    assertTrue(service.isMappedSchema(cachedFile))
  }

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
    val provider = service.getSchemaProvider(cachedFile)
    assertEquals("user", provider?.name)
    assertEquals(JsonSchemaVersion.SCHEMA_7, provider?.schemaVersion)
    assertTrue(service.isMappedSchema(cachedFile))
  }

  fun testIsMappedSchemaDoesNotRecomputeWhenNotAllowed() {
    val cachedFile = myFixture.addFileToProject("cached/pyproject.json", "{\"type\":\"object\"}").virtualFile
    markAsCachedContent(cachedFile, PYPROJECT_SCHEMA_URL, PYPROJECT_SCHEMA_REDIRECT_URL)

    val service = JsonSchemaService.Impl.get(project) as JsonSchemaServiceImpl
    service.reset()

    assertFalse(service.isMappedSchema(cachedFile, false))
    assertTrue(service.isMappedSchema(cachedFile, true))
    assertTrue(service.isMappedSchema(cachedFile, false))

    service.reset()
    assertFalse(service.isMappedSchema(cachedFile, false))
  }

  private fun markAsCachedContent(file: VirtualFile, requestUrl: String, retrievalUrl: String) {
    file.putUserData(SchemaOrigin.REQUEST_URL_KEY, SchemaUrl.parse(requestUrl).value)
    file.putUserData(SchemaOrigin.URL_KEY, SchemaUrl.parse(retrievalUrl).value)
  }

  private fun getImplicitProvider(file: VirtualFile): JsonSchemaFileProvider {
    return JsonSchemaCatalogImplicitProviderFactory().getProviders(project).single { it.isAvailable(file) }
  }

  private fun getSchemaUrls(file: VirtualFile): List<String> {
    return JsonSchemaService.Impl.get(project).getSchemaFilesForFile(file).map { it.url }
  }

  private fun getSingleSchemaUrls(file: VirtualFile): List<String> {
    val files = (JsonSchemaService.Impl.get(project) as JsonSchemaServiceImpl).getSchemasForFile(file, true, false)
    return files.map { it.url }
  }

  companion object {
    private const val PYPROJECT_SCHEMA_URL = "https://json.schemastore.org/pyproject.json"
    private const val PYPROJECT_SCHEMA_REDIRECT_URL = "https://www.schemastore.org/pyproject.json"
    private const val RUFF_SCHEMA_URL = "https://www.schemastore.org/ruff.json"
  }
}
