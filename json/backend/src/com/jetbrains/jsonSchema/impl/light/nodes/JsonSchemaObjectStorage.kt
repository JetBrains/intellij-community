// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.openapi.vfs.impl.http.RemoteFileState
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.containers.ConcurrentFactoryMap
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.util.concurrent.CancellationException

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class JsonSchemaObjectStorage {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): JsonSchemaObjectStorage {
      return project.service<JsonSchemaObjectStorage>()
    }
  }

  private data class SchemaId(val schemaFile: VirtualFile, val modificationStamp: Long)

  private val parsedSchemaById = ConcurrentFactoryMap.createMap<SchemaId, JsonSchemaObject> { id ->
    createRootSchemaObject(id.schemaFile)
  }

  fun getOrComputeSchemaRootObject(schemaFile: VirtualFile): JsonSchemaObject? {
    if (!isSupportedSchemaFile(schemaFile)) return null

    return parsedSchemaById[schemaFile.asSchemaId()]
      .takeIf { it !is MissingJsonSchemaObject }
  }

  fun getComputedSchemaRootOrNull(maybeSchemaFile: VirtualFile): JsonSchemaObject? {
    if (!parsedSchemaById.containsKey(maybeSchemaFile.asSchemaId())) return null

    return parsedSchemaById[maybeSchemaFile.asSchemaId()]
      .takeIf { it !is MissingJsonSchemaObject }
  }


  private fun isSupportedSchemaFile(maybeSchemaFile: VirtualFile): Boolean {
    return isSupportedSchemaFileType(maybeSchemaFile.fileType)
           && (maybeSchemaFile !is HttpVirtualFile || isLoadedHttpFile(maybeSchemaFile))
  }

  private fun isSupportedSchemaFileType(fileType: FileType): Boolean {
    return fileType.name in supportedFileTypeNames
  }

  private val supportedFileTypeNames = setOf("JSON", "JSON5", "YAML")

  private fun isLoadedHttpFile(maybeHttpFile: VirtualFile): Boolean {
    return maybeHttpFile is HttpVirtualFile && maybeHttpFile.fileInfo?.state == RemoteFileState.DOWNLOADED
  }

  private fun VirtualFile.asSchemaId(): SchemaId {
    return if (this is LightVirtualFile) {
      SchemaId(this, -1)
    }
    else {
      SchemaId(this, this.modificationStamp)
    }
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
      "JSON", "JSON5" -> json5ObjectMapper
      "YAML" -> yamlObjectMapper
      else -> {
        Logger.getInstance("JsonSchemaReader").warn("Unsupported json schema file type: $providedFileTypeId")
        return null
      }
    }
    return try {
      schemaFile.inputStream.use<InputStream, JsonNode?>(suitableReader::readTree)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (exception: Exception) {
      Logger.getInstance("JsonSchemaReader2").warn("Unable to parse JSON schema from the given file '${schemaFile.name}'", exception)
      null
    }
  }
}

internal val json5ObjectMapper = JsonMapper(
  JsonFactory.builder()
    .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
    .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
    .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
    .enable(JsonReadFeature.ALLOW_MISSING_VALUES)
    .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
    .enable(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
    .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
    .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
    .build()
).enable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION)


internal val yamlObjectMapper = ObjectMapper(
  YAMLFactory.builder()
    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    .build()
)