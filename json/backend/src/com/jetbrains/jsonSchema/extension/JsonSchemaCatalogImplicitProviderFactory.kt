// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.JsonSchemaCatalogEntry
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration
import com.jetbrains.jsonSchema.impl.JsonCachedValues
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogEntryFileMatcher

class JsonSchemaCatalogImplicitProviderFactory : JsonSchemaProviderFactory, DumbAware {
  override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
    if (!JsonSchemaCatalogProjectConfiguration.getInstance(project).isImplicitSchemasEnabled) return emptyList()

    val catalogFile = JsonSchemaProviderFactory.getResourceFile(javaClass, IMPLICIT_CATALOG_PATH) ?: return emptyList()
    val catalogEntries = JsonCachedValues.getSchemaCatalog(catalogFile, project) ?: return emptyList()
    return catalogEntries.mapNotNull { entry ->
      if (entry.fileMasks.isEmpty()) null else ImplicitSchemaProvider(project, entry)
    }
  }

  private class ImplicitSchemaProvider(
    private val project: Project,
    private val entry: JsonSchemaCatalogEntry,
  ) : JsonSchemaFileProvider {
    private val matcher = JsonSchemaCatalogEntryFileMatcher(entry)
    private var schemaFile: VirtualFile? = null

    override fun isAvailable(file: VirtualFile): Boolean {
      return file.isValid && !file.isDirectory && matcher.matches(file, project)
    }

    override fun getName(): String = presentableName

    override fun getSchemaFile(): VirtualFile? {
      if (schemaFile == null || !schemaFile!!.isValid) {
        schemaFile = JsonFileResolver.urlToFile(entry.url)
      }
      return schemaFile
    }

    override fun getSchemaType(): SchemaType = SchemaType.remoteSchema

    override fun isUserVisible(): Boolean = false

    override fun getPresentableName(): String {
      return StringUtil.notNullize(entry.name, StringUtil.notNullize(entry.description, entry.url))
    }

    override fun getRemoteSource(): String = entry.url
  }

  companion object {
    private const val IMPLICIT_CATALOG_PATH = "/jsonSchema/implicitSchemas.json"
  }
}
