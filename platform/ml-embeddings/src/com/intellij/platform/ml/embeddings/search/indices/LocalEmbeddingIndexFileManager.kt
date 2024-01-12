// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.indices

import ai.grazie.emb.FloatTextEmbedding
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.util.io.outputStream
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.exists
import kotlin.io.path.inputStream

class LocalEmbeddingIndexFileManager(root: Path, private val dimensions: Int = DEFAULT_DIMENSIONS) {
  private val lock = ReentrantReadWriteLock()
  private val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

  private val prettyPrinter = DefaultPrettyPrinter().apply { indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE) }

  private val rootPath = root
    get() = field.also { Files.createDirectories(field) }
  private val idsPath
    get() = rootPath.resolve(IDS_FILENAME)
  private val embeddingsPath
    get() = rootPath.resolve(EMBEDDINGS_FILENAME)

  val embeddingSizeInBytes = dimensions * EMBEDDING_ELEMENT_SIZE

  /** Provides reading access to the embedding vector at the specified index
   *  without reading the whole file into memory
   */
  operator fun get(index: Int): FloatTextEmbedding = lock.read {
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
  operator fun set(index: Int, embedding: FloatTextEmbedding) = lock.write {
    RandomAccessFile(embeddingsPath.toFile(), "rw").use { output ->
      output.seek(getIndexOffset(index))
      val buffer = ByteBuffer.allocate(EMBEDDING_ELEMENT_SIZE)
      embedding.values.forEach {
        output.write(buffer.putFloat(0, it).array())
      }
    }
  }

  /**
   * Removes the embedding vector at the specified index.
   * To do so, replaces this vector with the last vector in the file and shrinks the file size.
   */
  fun removeAtIndex(index: Int) = lock.write {
    RandomAccessFile(embeddingsPath.toFile(), "rw").use { file ->
      if (file.length() < embeddingSizeInBytes) return
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

  suspend fun loadIndex(): Pair<List<String>, List<FloatTextEmbedding>>? = coroutineScope {
    ensureActive()
    lock.read {
      ensureActive()
      if (!idsPath.exists() || !embeddingsPath.exists()) return@coroutineScope null
      val ids = mapper.readValue<List<String>>(idsPath.toFile()).map { it.intern() }.toMutableList()
      val buffer = ByteArray(EMBEDDING_ELEMENT_SIZE)
      embeddingsPath.inputStream().buffered().use { input ->
        ids to ids.map {
          ensureActive()
          FloatTextEmbedding(FloatArray(dimensions) {
            input.read(buffer)
            ByteBuffer.wrap(buffer).getFloat()
          })
        }
      }
    }
  }

  fun saveIds(ids: List<String>) = lock.write {
    withNotEnoughSpaceCheck {
      idsPath.outputStream().buffered().use { output ->
        mapper.writer(prettyPrinter).writeValue(output, ids)
      }
    }
  }

  suspend fun saveIndex(ids: List<String>, embeddings: List<FloatTextEmbedding>) = coroutineScope {
    ensureActive()
    lock.write {
      ensureActive()
      withNotEnoughSpaceCheck {
        idsPath.outputStream().buffered().use { output ->
          mapper.writer(prettyPrinter).writeValue(output, ids)
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

  private fun withNotEnoughSpaceCheck(task: () -> Unit) {
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
    const val DEFAULT_DIMENSIONS = 128
    const val EMBEDDING_ELEMENT_SIZE = 4

    private const val IDS_FILENAME = "ids.json"
    private const val EMBEDDINGS_FILENAME = "embeddings.bin"
  }
}
