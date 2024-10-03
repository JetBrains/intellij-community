// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableEntity
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableFile
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

internal val FILE_NAME_EMBEDDING_INDEX_NAME = ID.create<EmbeddingKey, String>("FileNameEmbeddingIndex")
internal val CLASS_NAME_EMBEDDING_INDEX_NAME = ID.create<EmbeddingKey, String>("ClassNameEmbeddingIndex")
internal val SYMBOL_NAME_EMBEDDING_INDEX_NAME = ID.create<EmbeddingKey, String>("SymbolNameEmbeddingIndex")

@JvmInline
internal value class EmbeddingKey(val textHashCode: Int) {
  fun toLong(fileId: Int): Long {
    // https://stackoverflow.com/a/12772968
    return (fileId.toLong() shl 32) or (0xffffffffL and textHashCode.toLong())
  }
}

internal class FileNameEmbeddingIndex : BaseEmbeddingIndex() {
  override fun getName(): ID<EmbeddingKey, String> = FILE_NAME_EMBEDDING_INDEX_NAME
  override fun getVersion(): Int = 1
  override fun index(inputData: FileContent): List<IndexableEntity> {
    return listOf(IndexableFile(inputData.file))
  }

  override fun getInputFilter(): FileBasedIndex.InputFilter {
    return FileBasedIndex.InputFilter { file ->
      // todo: we don't want to index libraries
      //   file.isInLocalFileSystem filters out jar libraries but it'll probably won't work e.g. for Python libs
      //   probably filtering should be implemented via FilePropertyPusher
      isAcceptableFile(file)
    }
  }

  override fun dependsOnFileContent(): Boolean = false
}

internal class ClassNameEmbeddingIndex : PsiBaseEmbeddingIndex() {
  override val fileTypes: Array<FileType>
    get() = ClassesProvider.supportedFileTypes

  override fun getName(): ID<EmbeddingKey, String> = CLASS_NAME_EMBEDDING_INDEX_NAME
  override fun getVersion(): Int = 2
  override fun index(inputData: FileContent): List<IndexableEntity> {
    return ClassesProvider.extractClasses(inputData)
  }
}

internal class SymbolNameEmbeddingIndex : PsiBaseEmbeddingIndex() {
  override val fileTypes: Array<FileType>
    get() = SymbolsProvider.supportedFileTypes

  override fun getName(): ID<EmbeddingKey, String> = SYMBOL_NAME_EMBEDDING_INDEX_NAME
  override fun getVersion(): Int = 2
  override fun index(inputData: FileContent): List<IndexableEntity> {
    return SymbolsProvider.extractSymbols(inputData)
  }
}

internal abstract class BaseEmbeddingIndex : FileBasedIndexExtension<EmbeddingKey, String>() {
  override fun traceKeyHashToVirtualFileMapping(): Boolean = true

  override fun getIndexer(): DataIndexer<EmbeddingKey, String, FileContent> {
    return DataIndexer { inputData ->
      index(inputData).associate { entity ->
        // Hash code should be calculated from indexable representation, not identifier
        val textHashcode = entity.indexableRepresentation.hashCode()
        // TODO: link to indexable representation (e.g. TextRange in file) should be preserved
        //  in index values besides id because we calculate vectors based on it
        EmbeddingKey(textHashcode) to entity.id.id
      }
    }
  }

  abstract fun index(context: FileContent): List<IndexableEntity>

  override fun getKeyDescriptor(): KeyDescriptor<EmbeddingKey> {
    return object : KeyDescriptor<EmbeddingKey> {
      override fun getHashCode(value: EmbeddingKey): Int = value.hashCode()
      override fun isEqual(val1: EmbeddingKey, val2: EmbeddingKey): Boolean = val1 == val2
      override fun save(out: DataOutput, value: EmbeddingKey) = DataInputOutputUtil.writeINT(out, value.textHashCode)
      override fun read(`in`: DataInput): EmbeddingKey = EmbeddingKey(DataInputOutputUtil.readINT(`in`))
    }
  }

  override fun getValueExternalizer(): DataExternalizer<String> {
    return object : DataExternalizer<String> {
      override fun save(out: DataOutput, value: String) = out.writeUTF(value)
      override fun read(`in`: DataInput): String = `in`.readUTF()
    }
  }
}

internal abstract class PsiBaseEmbeddingIndex : BaseEmbeddingIndex() {
  override fun getInputFilter(): FileBasedIndex.InputFilter {
    return object : DefaultFileTypeSpecificInputFilter(*fileTypes) {
      override fun acceptInput(file: VirtualFile): Boolean {
        // todo: we don't want to index libraries
        //   file.isInLocalFileSystem filters out jar libraries but it'll probably won't work e.g. for Python libs
        //   probably filtering should be implemented via FilePropertyPusher
        return isAcceptableFile(file)
      }
    }
  }

  override fun dependsOnFileContent(): Boolean = true

  abstract val fileTypes: Array<FileType>
}

private fun isAcceptableFile(file: VirtualFile): Boolean {
  return file.isInLocalFileSystem
}
