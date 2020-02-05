// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.index.SharedIndexExtensions
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.hash.ContentHashEnumerator
import com.intellij.util.indexing.hash.FileContentHashIndex
import com.intellij.util.indexing.hash.FileContentHashIndexExtension
import com.intellij.util.indexing.hash.SharedIndexChunk
import com.intellij.util.indexing.hash.building.IndexChunk
import com.intellij.util.indexing.hash.building.IndexesExporter
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.snapshot.IndexedHashesSupport
import com.intellij.util.indexing.snapshot.OneRecordValueContainer
import com.intellij.util.indexing.zipFs.UncompressedZipFileSystem
import junit.framework.AssertionFailedError
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

private const val CHUNK_NAME = "source"

@SkipSlowTestLocally
class SharedIndexDumpTest : LightJavaCodeInsightFixtureTestCase() {
  fun testOpenSharedIndexes() {
    val indexZipPath = generateTestSharedIndex()
    val chunkId = 1
    val sourceFileId = (getSourceFile() as VirtualFileWithId).id

    val hashIndex = FileContentHashIndex(FileContentHashIndexExtension(), object : IndexStorage<Long?, Void?> {
      override fun clear() = throw AssertionFailedError()

      override fun clearCaches() = throw AssertionFailedError()

      override fun removeAllValues(key: Long, inputId: Int) = throw AssertionFailedError()

      override fun flush() = throw AssertionFailedError()

      override fun addValue(key: Long?, inputId: Int, value: Void?) = throw AssertionFailedError()

      override fun close() = throw AssertionFailedError()

      override fun read(key: Long?): ValueContainer<Void?> {
        return OneRecordValueContainer(sourceFileId, null)
      }
    })

    var readFileId: Int? = null
    UncompressedZipFileSystem.create(indexZipPath).use { zipFs ->
      return ContentHashEnumerator(zipFs.getPath(CHUNK_NAME, "hashes")).use { hashEnumerator ->
        val idIndexChunk = SharedIndexChunk(zipFs.getPath(CHUNK_NAME), IdIndex.NAME, chunkId, hashEnumerator, 0)
        val findExtension = SharedIndexExtensions.findExtension(IdIndex.NAME.getExtension())
        val extension = IdIndex.NAME.getExtension()
        val index = idIndexChunk.open(findExtension, extension, hashIndex)
        index.getData(IdIndexEntry("methodCall", true)).forEach { id, _ ->
          readFileId = id
          true
        }

        assertNotNull(readFileId)
        assertEquals(sourceFileId, readFileId)
      }
    }
  }

  fun testSharedIndexHashes() {
    val indexZipPath = generateTestSharedIndex()
    val indexFs = UncompressedZipFileSystem.create(indexZipPath)

    val hashEnumerator = indexFs.getPath(CHUNK_NAME, "hashes")

    val hash = getSourceFileHash()

    val hashId = ContentHashEnumerator(hashEnumerator).use {
      it.tryEnumerate(hash)
    }

    assertTrue(hashId > 0)
  }

  fun testSharedIndexLayout() {
    val indexZipPath = generateTestSharedIndex()
    val actualFiles = UncompressedZipFileSystem.create(indexZipPath).use {
      val root = it.rootDirectories.first()
      Files.walk(root).map { p -> p.toString() }.sorted().collect(Collectors.joining("\n")).trimStart()
    }

    assertEquals("""
      source
      source/IdIndex
      source/IdIndex/IdIndex.forward
      source/IdIndex/IdIndex.forward.len
      source/IdIndex/IdIndex.forward.values.at
      source/IdIndex/IdIndex.forward_i
      source/IdIndex/IdIndex.forward_i.len
      source/IdIndex/IdIndex.storage
      source/IdIndex/IdIndex.storage.len
      source/IdIndex/IdIndex.storage.values.at
      source/IdIndex/IdIndex.storage_i
      source/IdIndex/IdIndex.storage_i.len
      source/Stubs
      source/Stubs/Stubs.storage
      source/Stubs/Stubs.storage.len
      source/Stubs/Stubs.storage.values.at
      source/Stubs/Stubs.storage_i
      source/Stubs/Stubs.storage_i.len
      source/Stubs/java.class.fqn
      source/Stubs/java.class.fqn/java.class.fqn.forward
      source/Stubs/java.class.fqn/java.class.fqn.forward.len
      source/Stubs/java.class.fqn/java.class.fqn.storage
      source/Stubs/java.class.fqn/java.class.fqn.storage.len
      source/Stubs/java.class.fqn/java.class.fqn.storage.values.at
      source/Stubs/java.class.fqn/java.class.fqn.storage_i
      source/Stubs/java.class.fqn/java.class.fqn.storage_i.len
      source/Stubs/java.class.shortname
      source/Stubs/java.class.shortname/java.class.shortname.forward
      source/Stubs/java.class.shortname/java.class.shortname.forward.len
      source/Stubs/java.class.shortname/java.class.shortname.storage
      source/Stubs/java.class.shortname/java.class.shortname.storage.keystream
      source/Stubs/java.class.shortname/java.class.shortname.storage.keystream.len
      source/Stubs/java.class.shortname/java.class.shortname.storage.len
      source/Stubs/java.class.shortname/java.class.shortname.storage.values.at
      source/Stubs/java.class.shortname/java.class.shortname.storage_i
      source/Stubs/java.class.shortname/java.class.shortname.storage_i.len
      source/Stubs/java.method.name
      source/Stubs/java.method.name/java.method.name.forward
      source/Stubs/java.method.name/java.method.name.forward.len
      source/Stubs/java.method.name/java.method.name.storage
      source/Stubs/java.method.name/java.method.name.storage.keystream
      source/Stubs/java.method.name/java.method.name.storage.keystream.len
      source/Stubs/java.method.name/java.method.name.storage.len
      source/Stubs/java.method.name/java.method.name.storage.values.at
      source/Stubs/java.method.name/java.method.name.storage_i
      source/Stubs/java.method.name/java.method.name.storage_i.len
      source/Stubs/serializerNames
      source/Stubs/serializerNames/names
      source/Stubs/serializerNames/names.keystream
      source/Stubs/serializerNames/names.keystream.len
      source/Stubs/serializerNames/names.len
      source/Stubs/serializerNames/names_i
      source/Stubs/serializerNames/names_i.len
      source/Trigram.Index
      source/Trigram.Index/Trigram.Index.forward
      source/Trigram.Index/Trigram.Index.forward.len
      source/Trigram.Index/Trigram.Index.forward.values.at
      source/Trigram.Index/Trigram.Index.forward_i
      source/Trigram.Index/Trigram.Index.forward_i.len
      source/Trigram.Index/Trigram.Index.storage
      source/Trigram.Index/Trigram.Index.storage.len
      source/Trigram.Index/Trigram.Index.storage.values.at
      source/Trigram.Index/Trigram.Index.storage_i
      source/Trigram.Index/Trigram.Index.storage_i.len
      source/empty-indices.txt
      source/empty-stub-indices.txt
      source/hashes
      source/hashes.keystream
      source/hashes.keystream.len
      source/hashes.len
      source/hashes_i
      source/hashes_i.len
      source/java.null.method.argument
      source/java.null.method.argument/java.null.method.argument.forward
      source/java.null.method.argument/java.null.method.argument.forward.len
      source/java.null.method.argument/java.null.method.argument.forward.values.at
      source/java.null.method.argument/java.null.method.argument.forward_i
      source/java.null.method.argument/java.null.method.argument.forward_i.len
      source/java.null.method.argument/java.null.method.argument.storage
      source/java.null.method.argument/java.null.method.argument.storage.keystream
      source/java.null.method.argument/java.null.method.argument.storage.keystream.len
      source/java.null.method.argument/java.null.method.argument.storage.len
      source/java.null.method.argument/java.null.method.argument.storage.values.at
      source/java.null.method.argument/java.null.method.argument.storage_i
      source/java.null.method.argument/java.null.method.argument.storage_i.len
      source/java.simple.property
      source/java.simple.property/java.simple.property.storage
      source/java.simple.property/java.simple.property.storage.len
      source/java.simple.property/java.simple.property.storage.values.at
      source/java.simple.property/java.simple.property.storage_i
      source/java.simple.property/java.simple.property.storage_i.len
    """.trimIndent(), actualFiles)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <K, V> ID<K, V>.getExtension(): FileBasedIndexExtension<K, V> {
    return FileBasedIndexExtension.EXTENSION_POINT_NAME.findFirstSafe{ it.name == this } as FileBasedIndexExtension<K, V>
  }

  private fun getSourceFileHash(): ByteArray {
    val content = FileContentImpl.createByFile(getSourceFile(), project) as FileContentImpl
    return IndexedHashesSupport.getOrInitIndexedHash(content, false)
  }

  private fun getSourceFile() = myFixture.findClass("A").containingFile.virtualFile

  private fun generateTestSharedIndex(): Path {
    val file = myFixture.configureByText("A.java", """
        public class A { 
          public void foo() {
            //Comment
            methodCall(null)
          }
          
          public String getName() {
            return name;
          }
        }
      """.trimIndent()).virtualFile

    val tempDir = FileUtil.createTempDirectory("shared-indexes-test", "").toPath()
    val indexZipPath = tempDir.resolve("shared-index.zip")

    val chunks = arrayListOf<IndexChunk>()
    chunks += IndexChunk(setOf(file), CHUNK_NAME)

    IndexesExporter
      .getInstance(project)
      .exportIndices(chunks, indexZipPath, EmptyProgressIndicator())
    return indexZipPath
  }
}