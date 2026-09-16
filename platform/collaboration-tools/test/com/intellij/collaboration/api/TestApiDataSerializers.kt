// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.intellij.collaboration.api.dto.GraphQLErrorDTO
import com.intellij.collaboration.api.dto.GraphQLResponseDTO
import com.intellij.collaboration.api.graphql.GraphQLDataDeserializer
import com.intellij.collaboration.api.json.JsonDataDeserializer
import com.intellij.collaboration.api.json.JsonDataSerializer
import java.io.InputStream
import java.io.Reader
import java.nio.charset.Charset

/**
 * A minimal Jackson-based [JsonDataSerializer] + [JsonDataDeserializer] used by the API-helper integration tests.
 * Mirrors the behavior of the production (GitLab/GitHub) serializers closely enough to exercise the helpers:
 * an empty reader/stream is deserialized into `null` rather than throwing.
 */
internal object TestJsonDataSerializer : JsonDataSerializer, JsonDataDeserializer {
  val mapper: ObjectMapper = jacksonMapperBuilder()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .build()

  override fun toJsonBytes(content: Any): ByteArray = mapper.writeValueAsBytes(content)

  override fun <T> fromJson(bodyReader: Reader, clazz: Class<T>): T? =
    mapper.createParser(bodyReader).readValueAsTree<JsonNode>()?.let { mapper.treeToValue(it, clazz) }

  override fun <T> fromJson(bodyReader: Reader, clazz: Class<T>, vararg classArgs: Class<*>): T? {
    val type = mapper.typeFactory.constructParametricType(clazz, *classArgs)
    return mapper.createParser(bodyReader).readValueAsTree<JsonNode>()?.let { mapper.treeToValue(it, type) }
  }

  override fun <T : Any> readJson(stream: InputStream, charset: Charset, clazz: Class<T>): T? =
    fromJson(stream.reader(charset), clazz)

  override fun <T : Any> readJson(stream: InputStream, charset: Charset, clazz: Class<T>, vararg classArgs: Class<*>): T? =
    fromJson(stream.reader(charset), clazz, *classArgs)
}

/**
 * A minimal Jackson-based [GraphQLDataDeserializer] used by the GraphQL API-helper integration tests.
 * Reuses the mapper from [TestJsonDataSerializer] and mirrors the production path-traversal logic.
 */
internal object TestGraphQLDataDeserializer : GraphQLDataDeserializer {
  private val mapper: ObjectMapper get() = TestJsonDataSerializer.mapper

  override fun <T> readAndMapGQLResponse(bodyReader: Reader, pathFromData: Array<out String>, clazz: Class<T>)
    : GraphQLResponseDTO<T?, GraphQLErrorDTO> =
    readAndMap(pathFromData, clazz) { mapper.readValue(bodyReader, it) }

  override fun <T : Any> readAndMapGQLResponse(stream: InputStream, charset: Charset, pathFromData: Array<out String>, clazz: Class<T>)
    : GraphQLResponseDTO<T?, GraphQLErrorDTO> =
    readAndMap(pathFromData, clazz) { mapper.readValue(stream.reader(charset), it) }

  private fun <T> readAndMap(
    pathFromData: Array<out String>,
    clazz: Class<T>,
    responseSupplier: (JavaType) -> GraphQLResponseDTO<out JsonNode, GraphQLErrorDTO>,
  ): GraphQLResponseDTO<T?, GraphQLErrorDTO> {
    val responseType = mapper.typeFactory
      .constructParametricType(GraphQLResponseDTO::class.java, JsonNode::class.java, GraphQLErrorDTO::class.java)
    val response = responseSupplier(responseType)
    val data = response.data
    if (data != null && !data.isNull) {
      var node: JsonNode = data
      for (path in pathFromData) {
        node = node[path] ?: break
      }
      if (!node.isNull) {
        return GraphQLResponseDTO(mapper.treeToValue(node, clazz), response.errors)
      }
    }
    return GraphQLResponseDTO(null, response.errors)
  }
}
