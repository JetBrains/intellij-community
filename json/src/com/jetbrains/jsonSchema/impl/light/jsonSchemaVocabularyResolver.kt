// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light

import com.intellij.json.JsonFileType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils.NULL_OBJ
import com.jetbrains.jsonSchema.impl.light.nodes.JacksonSchemaNodeAccessor
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

internal fun resolveVocabulary(searchedVocabularyId: String,
                               currentSchemaNode: JsonSchemaObjectBackedByJacksonBase,
                               jsonSchemaService: JsonSchemaService,
                               bundledVocabularies: List<StandardJsonSchemaVocabulary.Bundled>): JsonSchemaObject? {
  val instanceVocabularyIds = JacksonSchemaNodeAccessor.readNodeKeys(currentSchemaNode.getRootSchemaObject().rawSchemaNode, VOCABULARY)
                              ?: return null
  val vocabularyToLoad = findBundledVocabulary(searchedVocabularyId, instanceVocabularyIds, bundledVocabularies)
                         ?: findRemoteVocabulary(searchedVocabularyId, instanceVocabularyIds)
                         ?: return null

  val vocabularyVirtualFile = vocabularyToLoad.load() ?: return null
  return JsonSchemaObjectReadingUtils.downloadAndParseRemoteSchema(jsonSchemaService, vocabularyVirtualFile)
    ?.takeIf { it != NULL_OBJ } // todo get rid of this
}

private fun findRemoteVocabulary(maybeVocabularyId: String, instanceVocabularyIds: Sequence<String>): StandardJsonSchemaVocabulary? {
  return instanceVocabularyIds
    .map { id -> StandardJsonSchemaVocabulary.Remote(id, id) }
    .firstOrNull { remoteVocabulary -> remoteVocabulary.id.endsWith(maybeVocabularyId) }
}

private fun findBundledVocabulary(maybeVocabularyId: String,
                                  instanceVocabularyIds: Sequence<String>,
                                  bundledVocabularies: List<StandardJsonSchemaVocabulary.Bundled>): StandardJsonSchemaVocabulary? {
  return bundledVocabularies
    .firstOrNull { vocabulary ->
      vocabulary.remoteUrl.endsWith(maybeVocabularyId)
      && instanceVocabularyIds.any { instanceId -> vocabulary.id == instanceId }
    }
}

internal sealed class StandardJsonSchemaVocabulary(
  val id: String
) {
  abstract fun load(): VirtualFile?

  class Bundled(id: String,
                val remoteUrl: String,
                val resourcePath: String) : StandardJsonSchemaVocabulary(id) {
    private val loadedVocabularyFile: VirtualFile? by lazy {
      try {
        loadBundledSchemaUnsafe()
      }
      catch (exception: IOException) {
        thisLogger().warn("Unable to load bundled schema for vocabulary", exception)
        null
      }
    }

    override fun load(): VirtualFile? {
      return loadedVocabularyFile
    }

    private fun loadBundledSchemaUnsafe(): LightVirtualFile? {
      return this::class
        .java.classLoader
        .getResourceAsStream(resourcePath)
        ?.use(FileUtil::loadTextAndClose)
        ?.let { schemaText ->
          val schemaName = "${resourcePath.substringAfterLast("/")}.json"
          LightVirtualFile(schemaName, JsonFileType.INSTANCE, schemaText)
        }
    }
  }

  class Remote(id: String, val url: String) : StandardJsonSchemaVocabulary(id) {
    override fun load(): VirtualFile? {
      val remoteSchemaUrl = try {
        URL(url)
      }
      catch (exception: MalformedURLException) {
        thisLogger().warn("Unable to parse URL for json schema vocabulary", exception)
        return null
      }
      return VfsUtil.findFileByURL(remoteSchemaUrl)
    }
  }
}
