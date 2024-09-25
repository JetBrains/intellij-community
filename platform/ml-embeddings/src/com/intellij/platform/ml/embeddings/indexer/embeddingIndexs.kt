// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.PsiFile
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput
import java.util.*

private val CLASS_NAME_EMBEDDING_INDEX_NAME = ID.create<EmbeddingKey, String>("ClassNameEmbeddingIndex")
private val SYMBOL_NAME_EMBEDDING_INDEX_NAME = ID.create<EmbeddingKey, String>("SymbolNameEmbeddingIndex")

internal class EmbeddingKey(val fileId: Int, val indexableRepresentationHashCode: Int) {
  override fun hashCode(): Int = Objects.hash(fileId, indexableRepresentationHashCode)
  override fun equals(other: Any?): Boolean =
    other is EmbeddingKey && other.fileId == fileId && other.indexableRepresentationHashCode == indexableRepresentationHashCode

  fun toLong(): Long {
    return (fileId.toLong() shl 32) + indexableRepresentationHashCode.toLong()
  }

  companion object {
    fun fromLong(v: Long): EmbeddingKey {
      return EmbeddingKey((v shr 32).toInt(), v.toInt())
    }
  }
}

@JvmInline
value class IndexingItem(val text: String)

internal class ClassNameEmbeddingIndex : BaseEmbeddingIndex() {
  override val fileTypes: Array<FileType>
    get() = ClassesProvider.supportedFileTypes

  override fun getName(): ID<EmbeddingKey, String> = CLASS_NAME_EMBEDDING_INDEX_NAME
  override fun getVersion(): Int = 0
  override fun index(psiFile: PsiFile): List<IndexingItem> {
    return ClassesProvider.extractClasses(psiFile).map { IndexingItem(it.id.id) }
  }
}

internal class SymbolNameEmbeddingIndex : BaseEmbeddingIndex() {
  override val fileTypes: Array<FileType>
    get() = SymbolsProvider.supportedFileTypes

  override fun getName(): ID<EmbeddingKey, String> = SYMBOL_NAME_EMBEDDING_INDEX_NAME
  override fun getVersion(): Int = 0
  override fun index(psiFile: PsiFile): List<IndexingItem> {
    return SymbolsProvider.extractSymbols(psiFile).map { IndexingItem(it.id.id) }
  }
}

internal abstract class BaseEmbeddingIndex() : FileBasedIndexExtension<EmbeddingKey, String>() {
  override fun getInputFilter(): FileBasedIndex.InputFilter {
    return object : DefaultFileTypeSpecificInputFilter(*fileTypes) {
      override fun acceptInput(file: VirtualFile): Boolean {
        // todo: we don't want to index libraries
        //   file.isInLocalFileSystem filters out jar libraries but it'll probably won't work e.g. for Python libs
        //   probably filtering should be implemented via FilePropertyPusher
        return file.isInLocalFileSystem
      }
    }
  }

  override fun dependsOnFileContent(): Boolean = true

  override fun getIndexer(): DataIndexer<EmbeddingKey, String, FileContent> {
    return DataIndexer { inputData ->
      val id = (inputData.file as? VirtualFileWithId)?.id ?: return@DataIndexer emptyMap()
      index(inputData.psiFile).associate { item ->
        val textHashcode = item.text.hashCode()
        EmbeddingKey(id, textHashcode) to item.text
      }
    }
  }

  abstract fun index(psiFile: PsiFile): List<IndexingItem>

  abstract val fileTypes: Array<FileType>

  override fun getKeyDescriptor(): KeyDescriptor<EmbeddingKey> {
    return object : KeyDescriptor<EmbeddingKey> {
      override fun getHashCode(value: EmbeddingKey): Int = value.hashCode()
      override fun isEqual(val1: EmbeddingKey, val2: EmbeddingKey): Boolean = val1 == val2
      override fun save(out: DataOutput, value: EmbeddingKey) = DataInputOutputUtil.writeLONG(out, value.toLong())
      override fun read(`in`: DataInput): EmbeddingKey = EmbeddingKey.fromLong(DataInputOutputUtil.readLONG(`in`))
    }
  }

  override fun getValueExternalizer(): DataExternalizer<String> {
    return object : DataExternalizer<String> {
      override fun save(out: DataOutput, value: String) = out.writeUTF(value)
      override fun read(`in`: DataInput): String = `in`.readUTF()
    }
  }
}