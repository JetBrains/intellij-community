// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface JsonSchemaReferenceResolver {
  fun resolve(reference: String, referenceOwner: JsonSchemaObjectBackedByJacksonBase, service: JsonSchemaService): JsonSchemaObject?
}

internal data object LocalSchemaReferenceResolver : JsonSchemaReferenceResolver {
  override fun resolve(
    reference: String,
    referenceOwner: JsonSchemaObjectBackedByJacksonBase,
    service: JsonSchemaService,
  ): JsonSchemaObject? {
    return resolveLocalSchemaNode(reference, referenceOwner)
  }
}

internal data object RemoteSchemaReferenceResolver : JsonSchemaReferenceResolver {
  override fun resolve(
    reference: String,
    referenceOwner: JsonSchemaObjectBackedByJacksonBase,
    service: JsonSchemaService,
  ): JsonSchemaObject? {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().run {
      reportSchemaUsageFeature(JsonSchemaFusCountedFeature.RemoteUrlResolveRequest)
      reportUniqueUrlDownloadRequestUsage(reference)
    }
    // leave tests with default behaviour to not accidentally miss even more bugs
    if (!ApplicationManager.getApplication().isUnitTestMode && !Registry.`is`("json.schema.object.v2.enable.nested.remote.schema.resolve")) {
      return null
    }

    return resolveRemoteSchemaByUrl(reference, referenceOwner, service)
  }
}

internal abstract class VocabularySchemaReferenceResolver(private val bundledVocabularies: List<StandardJsonSchemaVocabulary.Bundled>) : JsonSchemaReferenceResolver {
  override fun resolve(
    reference: String,
    referenceOwner: JsonSchemaObjectBackedByJacksonBase,
    service: JsonSchemaService,
  ): JsonSchemaObject? {
    if (reference.startsWith("http") || reference.startsWith("#") || reference.startsWith("/")) return null
    return resolveVocabulary(reference, referenceOwner, service, bundledVocabularies)
  }
}

internal fun resolveLocalSchemaNode(
  maybeEmptyReference: String,
  currentSchemaNode: JsonSchemaObjectBackedByJacksonBase,
): JsonSchemaObject? {
  JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.LocalReferenceResolveRequest)
  return when {
    maybeEmptyReference.startsWith("#/") -> resolveReference(maybeEmptyReference, currentSchemaNode)
    maybeEmptyReference.startsWith("/") -> resolveReference(maybeEmptyReference, currentSchemaNode)
    maybeEmptyReference == "#" -> currentSchemaNode.rootSchemaObject
    maybeEmptyReference.startsWith("#") -> resolveIdOrDynamicAnchor(maybeEmptyReference, currentSchemaNode)
    else -> null
  }
}

private fun resolveRemoteSchemaByUrl(reference: String, schemaNode: JsonSchemaObject, service: JsonSchemaService): JsonSchemaObject? {
  val value = JsonSchemaObjectReadingUtils.fetchSchemaFromRefDefinition(reference, schemaNode, service, schemaNode.isRefRecursive)
  if (!JsonFileResolver.isHttpPath(reference)) {
    service.registerReference(reference)
  }
  else if (value != null) {
    // our aliases - if http ref actually refers to a local file with specific ID
    val virtualFile = service.resolveSchemaFile(value)
    if (virtualFile != null && virtualFile !is HttpVirtualFile) {
      service.registerReference(virtualFile.name)
    }
  }
  return value
}

private fun resolveIdOrDynamicAnchor(idOrAnchorName: String, currentSchemaNode: JsonSchemaObjectBackedByJacksonBase): JsonSchemaObject? {
  val maybeExistingIdOrAnchor = idOrAnchorName.trimStart('#')
  val effectiveSchemaNodePointer = currentSchemaNode.getRootSchemaObject().resolveDynamicAnchor(maybeExistingIdOrAnchor)
                                   ?: currentSchemaNode.getRootSchemaObject().resolveId(maybeExistingIdOrAnchor)
  if (effectiveSchemaNodePointer.isNullOrBlank()) return null
  return currentSchemaNode.getRootSchemaObject().getSchemaObjectByAbsoluteJsonPointer(effectiveSchemaNodePointer)
}

private fun resolveReference(reference: String, currentSchemaNode: JsonSchemaObjectBackedByJacksonBase): JsonSchemaObject? {
  val maybeCorrectJsonPointer = reference.trimStart('#')
  return currentSchemaNode.getRootSchemaObject()
    .getSchemaObjectByAbsoluteJsonPointer(maybeCorrectJsonPointer)
}