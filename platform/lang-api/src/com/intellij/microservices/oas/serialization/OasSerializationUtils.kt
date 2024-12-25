package com.intellij.microservices.oas.serialization

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.intellij.microservices.mime.MimeTypes
import com.intellij.microservices.oas.*
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.StringWriter
import java.io.Writer

@NlsSafe
fun generateOasPreview(openApiSpecification: OpenApiSpecification): String {
  val factory = YAMLFactory().configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)

  val writer = StringWriter()
  val mapper = ObjectMapper(factory)

  openApiSpecification.paths.forEachIndexed { index, model ->
    if (index != 0) {
      writer.buffer.append("\n---\n")
    }
    val generator = factory.createGenerator(writer)
    val root = mapper.createObjectNode()
    appendPathToJson(root, model)
    mapper.writeTree(generator, root)
  }

  val document = writer.toString()
  val paddingBuilder = StringBuilder()
  for (s in document.split("\n")) {
    if (s == "---") {
      paddingBuilder.append(s)
    }
    else {
      paddingBuilder.append("  ").append(s)
    }
    paddingBuilder.append("\n")
  }

  return paddingBuilder.toString().trimEnd()
}

@NlsSafe
fun generateOasDraft(projectName: String, models: OpenApiSpecification): String {
  val factory = YAMLFactory().configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
  val writer: Writer = StringWriter()
  generateOasContent(factory, projectName, models, writer)
  return writer.toString()
}

/**
 * @param prefixes a list of keys, that should wrap the generated schema.
 * For example if generated schema represents the `"#/components/schemas/Pojo"` and you pass `listOf("key1", "key2")` as [prefixes]
 * the result will be:
 * ```
 * {
 *   "type" : "object",
 *   "properties" : {
 *     "key1" : {
 *       "type" : "object",
 *       "properties" : {
 *         "key2" : {
 *           "$ref" : "#/components/schemas/Pojo"
 *         }
 *       }
 *     }
 *   },
 *   ...
 * ```
 * it is needed as a workaround for cases when validated by current schema JSON is nested in another JSON
 */
@NlsSafe
fun generateOasJsonSchemaForRequestBody(oasSpecification: OpenApiSpecification, prefixes: List<String> = emptyList()): String? {
  val schema = findSinglePathRequestBodyData(oasSpecification) ?: return null
  val mapper = ObjectMapper(JsonFactory())
  val rootNode = mapper.createObjectNode()
  val refNode = createNestedNodeWithSchemaPrefixes(rootNode, prefixes)
  appendSchema(refNode, schema)
  val components = oasSpecification.components
  if (components != null) {
    rootNode.putObject("components").apply {
      appendComponentsToJson(this, components)
    }
  }

  return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode)
}

fun findSinglePathRequestBodyData(openApiSpecification: OpenApiSpecification,
                                  contentType: String = MimeTypes.APPLICATION_JSON): OasSchema? {
  return openApiSpecification.paths.asSequence()
    .flatMap {
      it.operations.asSequence()
        .mapNotNull { it.requestBody?.content?.get(contentType) }
    }.firstOrNull()
}

@Internal
fun createNestedNodeWithSchemaPrefixes(rootNode: ObjectNode, prefixes: List<String>): ObjectNode =
  prefixes.fold(rootNode) { node, name ->
    node.put("type", "object")
    node.putObject("properties").putObject(name)
  }

@NlsSafe
fun generateOasContent(factory: JsonFactory, projectName: String, openApiSpecification: OpenApiSpecification, writer: Writer) {
  val mapper = ObjectMapper(factory)
  val safeProjectName = FileUtil.sanitizeFileName(projectName)

  val rootNode = mapper.createObjectNode()
  rootNode.put("openapi", "3.1.0")
  rootNode.putObject("info").apply {
    put("title", "$safeProjectName API")
    put("description", "$safeProjectName API")
    put("version", "1.0.0")
  }
  rootNode.putArray("servers").apply {
    addObject().put("url", "https://$safeProjectName")
  }

  if (!openApiSpecification.tags.isNullOrEmpty()) {
    appendTags(rootNode, openApiSpecification.tags)
  }

  val pathsNode = rootNode.putObject("paths")
  for (model in openApiSpecification.paths) {
    appendPathToJson(pathsNode, model)
  }

  if (openApiSpecification.components != null && openApiSpecification.components.schemas.isNotEmpty()) {
    val componentsNode = rootNode.putObject("components")
    appendComponentsToJson(componentsNode, openApiSpecification.components)
  }

  val generator = factory.createGenerator(writer)
  if (factory !is YAMLFactory) { // only for JSON output
    generator.useDefaultPrettyPrinter()
  }
  mapper.writeTree(generator, rootNode)
}

private fun appendPathToJson(rootNode: ObjectNode, model: OasEndpointPath) {
  if (model.path == OPEN_API_UNKNOWN_SEGMENT && model.operations.isEmpty()) return

  val pathNode = rootNode.putObject(model.absolutePath)
  if (model.summary != null) {
    pathNode.put("summary", model.summary)
  }
  for (operation in model.operations) {
    appendOperationToJson(pathNode, operation)
  }
}

private fun appendOperationToJson(pathNode: ObjectNode, operation: OasOperation) {
  val methodNode = pathNode.putObject(operation.method.methodName)
  if (operation.tags.isNotEmpty()) {
    appendOperationTags(methodNode, operation.tags)
  }
  if (operation.summary != null) {
    methodNode.put("summary", operation.summary)
  }
  if (operation.description != null) {
    methodNode.put("description", operation.description)
  }
  if (operation.isDeprecated) {
    methodNode.put("deprecated", true)
  }
  if (operation.operationId != null) {
    methodNode.put("operationId", operation.operationId)
  }

  if (operation.parameters.isNotEmpty()) {
    val parametersNode = methodNode.putArray("parameters")
    for (parameter in operation.parameters) {
      val parameterNode = parametersNode.addObject()
      parameterNode.put("name", parameter.name)
      parameterNode.put("in", parameter.inPlace.placeName)
      if (parameter.isDeprecated) {
        parameterNode.put("deprecated", true)
      }
      parameterNode.put("required", parameter.isRequired)
      if (!parameter.description.isNullOrEmpty()) {
        parameterNode.put("description", parameter.description)
      }

      val schema = parameter.schema
      if (schema != null) {
        val schemaNode = parameterNode.putObject("schema")
        appendSchema(schemaNode, schema)

        schema.default?.let {
          schemaNode.put("default", it)
        }
        
        appendEnum(schemaNode, schema)
      }
    }
  }

  if (operation.requestBody != null) {
    val requestBodyNode = methodNode.putObject("requestBody")

    val requestBodyContentNode = requestBodyNode.putObject("content")
    operation.requestBody.content.entries.forEach { contentEntry ->
      val (contentType, schema) = contentEntry

      val mediaTypeNode = requestBodyContentNode.putObject(contentType)

      val schemaNode = mediaTypeNode.putObject("schema")
      appendSchema(schemaNode, schema)

      if (schema.required != null) {
        val requiredArray = schemaNode.putArray("required")
        schema.required.forEach { requiredArray.add(it) }
      }

      schema.properties?.let { properties ->
        val propertiesNode = schemaNode.putObject("properties")
        properties.forEach { property ->
          appendSchema(propertiesNode.putObject(property.name), property.schema)
        }
      }
    }

    if (operation.requestBody.required) {
      requestBodyNode.put("required", true)
    }
  }

  if (operation.responses.isNotEmpty()) {
    val responsesNode = methodNode.putObject("responses")
    for (response in operation.responses) {
      val responseWithCodeNode = responsesNode.putObject(response.code)
      responseWithCodeNode.put("description", response.description ?: "")

      if (response.content.isNotEmpty()) {
        val contentNode = responseWithCodeNode.putObject("content")

        response.content.entries.forEach { (contentType, mediaTypeObject) ->
          val contentTypeNode = contentNode.putObject(contentType)
          appendSchema(contentTypeNode.putObject("schema"), mediaTypeObject.schema)

          if (mediaTypeObject.examples.isNotEmpty()) {
            appendExamplesToJson(contentTypeNode.putObject("examples"), mediaTypeObject.examples)
          }
        }
      }

      if (response.headers.isNotEmpty()) {
        val headersNode = responseWithCodeNode.putObject("headers")

        response.headers.forEach { (name, isRequired, schema) ->
          val headerNode = headersNode.putObject(name)
          headerNode.put("required", isRequired)
          appendSchema(headerNode.putObject("schema"), schema)
        }
      }
    }
  }
}

private fun appendTags(parentNode: ObjectNode, tags: List<OasTag>) {
  val tagsArray = parentNode.putArray("tags")
  tags.forEach { (name, description) ->
    val tagObject = tagsArray.addObject()
    tagObject.put("name", name)
    if (description.isNotEmpty()) {
      tagObject.put("description", description)
    }
  }
}

private fun appendOperationTags(parentNode: ObjectNode, tags: List<String>) {
  val tagsArray = parentNode.putArray("tags")
  tags.forEach(tagsArray::add)
}

private fun appendExamplesToJson(parentNode: ObjectNode, examples: Map<String, OasExample>) {
  examples.forEach { (name, example) ->
    val exampleObject = parentNode.putObject(name)
    example.summary?.let {
      exampleObject.put("summary", it)
    }
    example.description?.let {
      exampleObject.put("description", it)
    }
    appendExampleValueToJson(exampleObject, example.value, "value")
  }
}

private fun appendExampleValueToJson(exampleObject: ObjectNode, exampleValue: OasExampleValue, fieldName: String) {
  when (exampleValue) {
    OasNullValue -> exampleObject.put(fieldName, "null")
    is OasBooleanValue -> exampleObject.put(fieldName, exampleValue.value)
    is OasNumberValue -> when (exampleValue.value) {
      is Double -> exampleObject.put(fieldName, exampleValue.value)
      else -> exampleObject.put(fieldName, exampleValue.value.toLong())
    }
    is OasStringValue -> exampleObject.put(fieldName, exampleValue.value)
    is OasObjectValue -> {
      val objectValue = exampleObject.putObject(fieldName)
      exampleValue.properties.forEach { (name, value) ->
        appendExampleValueToJson(objectValue, value, name)
      }
    }
    is OasArrayValue -> {
      val arrayValue = exampleObject.putArray(fieldName)
      exampleValue.items.forEach { value ->
        appendItemToArray(arrayValue, value)
      }
    }
  }
}

private fun appendItemToArray(arrayNode: ArrayNode, exampleValue: OasExampleValue) {
  when (exampleValue) {
    OasNullValue -> return
    is OasBooleanValue -> arrayNode.add(exampleValue.value)
    is OasNumberValue -> when (exampleValue.value) {
      is Double -> arrayNode.add(exampleValue.value)
      else -> arrayNode.add(exampleValue.value.toLong())
    }
    is OasStringValue -> arrayNode.add(exampleValue.value)
    is OasObjectValue -> {
      val objectValue = arrayNode.addObject()
      exampleValue.properties.forEach { (name, value) ->
        appendExampleValueToJson(objectValue, value, name)
      }
    }
    is OasArrayValue -> {
      val arrayValue = arrayNode.addArray()
      exampleValue.items.forEach { value ->
        appendItemToArray(arrayValue, value)
      }
    }
  }
}


private fun appendComponentsToJson(componentsNode: ObjectNode, oasComponents: OasComponents) {
  if (oasComponents.schemas.isNotEmpty()) {
    val schemasNode = componentsNode.putObject("schemas")

    for ((qualifiedName, oasSchema) in oasComponents.schemas.entries) {
      val schemaNode = schemasNode.putObject(qualifiedName)

      oasSchema.type?.let {
        schemaNode.put("type", oasSchema.type.typeName)
      }
      oasSchema.properties?.let { properties ->
        val propertiesNode = schemaNode.putObject("properties")
        for (property in properties) {
          appendSchema(propertiesNode.putObject(property.name), property.schema)
        }
      }
      oasSchema.required?.let { required ->
        val requiredNode = schemaNode.putArray("required")
        required.forEach(requiredNode::add)
      }
    }
  }
}

private fun appendSchema(parentNode: ObjectNode, schema: OasSchema) {
  appendSchemaTypeAndFormat(parentNode, schema)

  if (!schema.reference.isNullOrEmpty()) {
    parentNode.put("\$ref", schema.reference)
  }

  appendEnum(parentNode, schema)

  if (schema.type == OasSchemaType.ARRAY) {
    schema.items?.let { itemSchema ->
      val itemsNode = parentNode.putObject("items")
      appendSchema(itemsNode, itemSchema)
    }
  }
}

private fun appendSchemaTypeAndFormat(schemaNode: ObjectNode, schema: OasSchema) {
  schema.type?.let {
    if (schema.isNullable) {
      val typeArray = schemaNode.putArray("type")
      typeArray.add(it.typeName)
      typeArray.add("null")
    }
    else {
      schemaNode.put("type", it.typeName)
    }
  }
  schema.format?.let {
    schemaNode.put("format", it.formatName)
  }
}

private fun appendEnum(parentNode: ObjectNode, schema: OasSchema) {
  schema.enum?.let { enumValues ->
    val enumNode = parentNode.putArray("enum")
    enumValues.forEach(enumNode::add)
  }
}