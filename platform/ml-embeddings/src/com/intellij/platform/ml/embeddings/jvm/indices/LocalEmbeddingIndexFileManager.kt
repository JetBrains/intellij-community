// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.jvm.indices

import ai.grazie.emb.FloatTextEmbedding
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.platform.ml.embeddings.jvm.utils.SuspendingReadWriteLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class LocalEmbeddingIndexFileManager(root: Path, private val dimensions: Int = DEFAULT_DIMENSIONS) {
  private val lock = SuspendingReadWriteLock()
  private val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

  private val prettyPrinter = DefaultPrettyPrinter().apply { indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE) }

  private val rootPath = root
    get() = field.also { Files.createDirectories(field) }
  private val idsPath
    get() = rootPath.resolve(IDS_FILENAME)
  private val sourceTypesPath
    get() = rootPath.resolve(SOURCE_TYPES_FILENAME)
  private val embeddingsPath
    get() = rootPath.resolve(EMBEDDINGS_FILENAME)

  val embeddingSizeInBytes: Int = dimensions * EMBEDDING_ELEMENT_SIZE

  /** Provides reading access to the embedding vector at the specified index
   *  without reading the whole file into memory
   */
  suspend fun get(index: Int): FloatTextEmbedding = lock.read {
    RandomAccessFile(embeddingsPath.toFile(), "r").use { input ->
      input.seek(getIndexOffset(index))
      val buffer = ByteArray(EMBEDDING_ELEMENT_SIZE)
      FloatTextEmbedding(FloatArray(dimensions) {
        input.read(buffer)
        ByteBuffer.wrap(buffer).getFloat()
      })
    }
  }

  /** Provides writing access to embedding vector at the specified index
   *  without writing the other vectors
   */
  suspend fun set(index: Int, embedding: FloatTextEmbedding) {
    lock.write {
      RandomAccessFile(embeddingsPath.toFile(), "rw").use { output ->
        output.seek(getIndexOffset(index))
        val buffer = ByteBuffer.allocate(EMBEDDING_ELEMENT_SIZE)
        embedding.values.forEach {
          output.write(buffer.putFloat(0, it).array())
        }
      }
    }
  }

  /**
   * Removes the embedding vector at the specified index.
   * To do so, replaces this vector with the last vector in the file and shrinks the file size.
   */
  suspend fun removeAtIndex(index: Int) {
    lock.write {
      RandomAccessFile(embeddingsPath.toFile(), "rw").use { file ->
        if (file.length() < embeddingSizeInBytes) return@write
        if (file.length() - embeddingSizeInBytes != getIndexOffset(index)) {
          file.seek(file.length() - embeddingSizeInBytes)
          val array = ByteArray(EMBEDDING_ELEMENT_SIZE)
          val embedding = FloatTextEmbedding(FloatArray(dimensions) {
            file.read(array)
            ByteBuffer.wrap(array).getFloat()
          })
          file.seek(getIndexOffset(index))
          val buffer = ByteBuffer.allocate(EMBEDDING_ELEMENT_SIZE)
          embedding.values.forEach {
            file.write(buffer.putFloat(0, it).array())
          }
        }
        file.setLength(file.length() - embeddingSizeInBytes)
      }
    }
  }

  data class LoadedIndex(val ids: List<EntityId>, val embeddings: List<FloatTextEmbedding>)

  suspend fun loadIndex(): LoadedIndex? = coroutineScope {
    lock.read {
      if (!idsPath.exists() || !sourceTypesPath.exists() || !embeddingsPath.exists()) return@read null
      try {
        val rawIds = mapper.readValue<List<String>>(idsPath.toFile())
        val sourceTypes = mapper.readValue<List<EntitySourceType>>(sourceTypesPath.toFile())
        val ids = (rawIds zip sourceTypes).map { EntityId(id = it.first, sourceType = it.second) }

        val buffer = ByteArray(EMBEDDING_ELEMENT_SIZE)
        val embeddings = embeddingsPath.inputStream().buffered().use { input ->
          ids.map {
            ensureActive()
            FloatTextEmbedding(FloatArray(dimensions) {
              input.read(buffer)
              ByteBuffer.wrap(buffer).getFloat()
            })
          }
        }
        LoadedIndex(ids, embeddings)
      }
      catch (_: JsonProcessingException) {
        return@read null
      }
    }
  }

  suspend fun saveIds(ids: List<EntityId>) {
    lock.write {
      withNotEnoughSpaceCheck {
        idsPath.outputStream().buffered().use { output ->
          mapper.writer(prettyPrinter).writeValue(output, ids)
        }
      }
    }
  }

  suspend fun saveIndex(ids: List<EntityId>, embeddings: List<FloatTextEmbedding>) {
    lock.write {
      val rawIds = ids.map { it.id }
      withNotEnoughSpaceCheck {
        idsPath.outputStream().buffered().use { output ->
          mapper.writer(prettyPrinter).writeValue(output, rawIds)
        }
      }
      val sourceTypesId = ids.map { it.sourceType }
      withNotEnoughSpaceCheck {
        sourceTypesPath.outputStream().buffered().use { output ->
          mapper.writer(prettyPrinter).writeValue(output, sourceTypesId)
        }
      }
      val buffer = ByteBuffer.allocate(EMBEDDING_ELEMENT_SIZE)
      withNotEnoughSpaceCheck {
        embeddingsPath.outputStream().buffered().use { output ->
          embeddings.forEach { embedding ->
            ensureActive()
            embedding.values.forEach {
              output.write(buffer.putFloat(0, it).array())
            }
          }
        }
      }
    }
  }

  private fun getIndexOffset(index: Int): Long = index.toLong() * embeddingSizeInBytes

  private suspend fun withNotEnoughSpaceCheck(task: CoroutineScope.() -> Unit) = coroutineScope {
    try {
      task()
    }
    catch (e: IOException) {
      if (e.message?.lowercase()?.contains("space") == true) {
        idsPath.toFile().delete()
        embeddingsPath.toFile().delete()
      }
      else throw e
    }
  }

  companion object {
    const val DEFAULT_DIMENSIONS: Int = 128
    const val EMBEDDING_ELEMENT_SIZE: Int = 4

    private const val IDS_FILENAME = "ids.json"
    private const val SOURCE_TYPES_FILENAME = "sourceTypes.json"
    private const val EMBEDDINGS_FILENAME = "embeddings.bin"
  }
}
