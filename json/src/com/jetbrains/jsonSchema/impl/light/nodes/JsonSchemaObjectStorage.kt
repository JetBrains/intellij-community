// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.openapi.vfs.impl.http.RemoteFileState
import com.intellij.util.containers.CollectionFactory
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import java.io.InputStream

@Service(Service.Level.PROJECT)
internal class JsonSchemaObjectStorage {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): JsonSchemaObjectStorage {
      return project.service<JsonSchemaObjectStorage>()
    }
  }

  private data class SchemaId(val fileId: Int, val modificationStamp: Long)

  private val parsedSchemaById = CollectionFactory.createConcurrentSoftValueMap<SchemaId, JsonSchemaObject>()

  fun getOrComputeSchemaRootObject(schemaFile: VirtualFile): JsonSchemaObject? {
    if (isNotLoadedHttpFile(schemaFile)) return null

    return parsedSchemaById.getOrPut(schemaFile.asSchemaId()) {
      createRootSchemaObject(schemaFile)
    }.takeIf { it !is MissingJsonSchemaObject }
  }

  fun getComputedSchemaRootOrNull(maybeSchemaFile: VirtualFile): JsonSchemaObject? {
    return parsedSchemaById[maybeSchemaFile.asSchemaId()]
  }

  private fun isNotLoadedHttpFile(maybeHttpFile: VirtualFile): Boolean {
    return maybeHttpFile is HttpVirtualFile && maybeHttpFile.fileInfo?.state != RemoteFileState.DOWNLOADED
  }

  private fun VirtualFile.asSchemaId(): SchemaId {
    return SchemaId(this.hashCode(), this.modificationStamp)
  }

  private fun createRootSchemaObject(schemaFile: VirtualFile): JsonSchemaObject {
    val parsedSchemaRoot = parseSchemaFileSafe(schemaFile)
    return if (parsedSchemaRoot == null)
      MissingJsonSchemaObject
    else
      RootJsonSchemaObjectBackedByJackson(parsedSchemaRoot, schemaFile)
  }

  private fun parseSchemaFileSafe(schemaFile: VirtualFile): JsonNode? {
    val suitableReader = when (val providedFileTypeId = schemaFile.fileType.name) {
      "JSON" -> jsonObjectMapper
      "YAML" -> yamlObjectMapper
      else -> {
        Logger.getInstance("JsonSchemaReader").warn("Unsupported json schema file type: $providedFileTypeId")
        return null
      }
    }
    return runCatching {
      schemaFile.inputStream.use<InputStream, JsonNode?>(suitableReader::readTree)
    }.getOrElse { exception ->
      Logger.getInstance("JsonSchemaReader2").warn("Unable to parse JSON schema from the given file '${schemaFile.name}'", exception)
      null
    }
  }
}

internal val jsonObjectMapper = JsonMapper()
  .enable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION)
internal val yamlObjectMapper = ObjectMapper(
  YAMLFactory.builder()
    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    .build()
)