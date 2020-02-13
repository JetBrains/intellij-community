// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.index.SharedIndexExtensions
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.impl.search.JavaNullMethodArgumentIndex
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.hash.ContentHashEnumerator
import com.intellij.util.indexing.hash.FileContentHashIndex
import com.intellij.util.indexing.hash.FileContentHashIndexExtension
import com.intellij.util.indexing.hash.SharedIndexChunk
import com.intellij.util.indexing.hash.SharedIndexStorageUtil
import com.intellij.util.indexing.hash.building.EmptyIndexEnumerator
import com.intellij.util.indexing.hash.building.IndexChunk
import com.intellij.util.indexing.hash.building.IndexesExporter
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.provided.SharedIndexChunkLocator
import com.intellij.util.indexing.snapshot.IndexedHashesSupport
import com.intellij.util.indexing.snapshot.OneRecordValueContainer
import com.intellij.util.indexing.zipFs.UncompressedZipFileSystem
import gnu.trove.THashMap
import junit.framework.AssertionFailedError
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

@SkipSlowTestLocally
class SharedIndexDumpTest : LightJavaCodeInsightFixtureTestCase() {
  private lateinit var tempDir: Path

  override fun setUp() {
    super.setUp()
    tempDir = FileUtil.createTempDirectory("shared-indexes-test", "").toPath()
    FileUtil.delete(tempDir)
    FileUtil.createDirectory(tempDir.toFile())
  }

  override fun tearDown() {
    try {
      super.tearDown()
    } finally {
      FileUtil.delete(tempDir)
    }
  }

  fun testOpenSharedIndexes() {
    val indexZipPath = generateTestSharedIndexChunk()
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
      return ContentHashEnumerator(zipFs.getPath("hashes")).use { hashEnumerator ->
        val findExtension = SharedIndexExtensions.findExtension(IdIndex.NAME.getExtension())
        val extension = IdIndex.NAME.getExtension()
        val idIndexChunk = SharedIndexChunk(zipFs.rootDirectory, IdIndex.NAME, chunkId, 0, false, findExtension, extension, hashIndex)
        val index = idIndexChunk.getIndex()
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
    val indexZipPath = generateTestSharedIndexChunk()
    val indexFs = UncompressedZipFileSystem.create(indexZipPath)

    val hashEnumerator = indexFs.getPath("hashes")

    val hash = getSourceFileHash()

    val hashId = ContentHashEnumerator(hashEnumerator).use {
      it.tryEnumerate(hash)
    }

    assertTrue(hashId > 0)
  }

  fun testEmptyIndexes() {
    val indexZipPath = generateTestSharedIndexChunk()

    var emptyIndexes :Set<String>? = null
    var emptyStubIndexes :Set<String>? = null

    UncompressedZipFileSystem.create(indexZipPath).use {
      val root = it.rootDirectory

      emptyIndexes = EmptyIndexEnumerator.readEmptyIndexes(root)
      emptyStubIndexes = EmptyIndexEnumerator.readEmptyStubIndexes(root)
    }

    assertFalse(emptyIndexes!!.isEmpty())
    assertFalse(emptyStubIndexes!!.isEmpty())

    assertTrue(emptyIndexes!!.contains("TodoIndex"))
    assertTrue(emptyStubIndexes!!.contains("gr.method.name"))
  }

  fun testSharedIndexLayout() {
    val indexZipPath = generateTestSharedIndexChunk()
    val actualFiles = UncompressedZipFileSystem.create(indexZipPath).use {
      val root = it.rootDirectory
      Files.walk(root).map { p -> p.toString() }.sorted().collect(Collectors.joining("\n")).trimStart()
    }

    assertEquals("""
      IdIndex
      IdIndex/IdIndex.forward
      IdIndex/IdIndex.forward.len
      IdIndex/IdIndex.forward.values.at
      IdIndex/IdIndex.forward_i
      IdIndex/IdIndex.forward_i.len
      IdIndex/IdIndex.storage
      IdIndex/IdIndex.storage.len
      IdIndex/IdIndex.storage.values.at
      IdIndex/IdIndex.storage_i
      IdIndex/IdIndex.storage_i.len
      Stubs
      Stubs/Stubs.storage
      Stubs/Stubs.storage.len
      Stubs/Stubs.storage.values.at
      Stubs/Stubs.storage_i
      Stubs/Stubs.storage_i.len
      Stubs/java.class.fqn
      Stubs/java.class.fqn/java.class.fqn.forward
      Stubs/java.class.fqn/java.class.fqn.forward.len
      Stubs/java.class.fqn/java.class.fqn.storage
      Stubs/java.class.fqn/java.class.fqn.storage.len
      Stubs/java.class.fqn/java.class.fqn.storage.values.at
      Stubs/java.class.fqn/java.class.fqn.storage_i
      Stubs/java.class.fqn/java.class.fqn.storage_i.len
      Stubs/java.class.shortname
      Stubs/java.class.shortname/java.class.shortname.forward
      Stubs/java.class.shortname/java.class.shortname.forward.len
      Stubs/java.class.shortname/java.class.shortname.storage
      Stubs/java.class.shortname/java.class.shortname.storage.keystream
      Stubs/java.class.shortname/java.class.shortname.storage.keystream.len
      Stubs/java.class.shortname/java.class.shortname.storage.len
      Stubs/java.class.shortname/java.class.shortname.storage.values.at
      Stubs/java.class.shortname/java.class.shortname.storage_i
      Stubs/java.class.shortname/java.class.shortname.storage_i.len
      Stubs/java.method.name
      Stubs/java.method.name/java.method.name.forward
      Stubs/java.method.name/java.method.name.forward.len
      Stubs/java.method.name/java.method.name.storage
      Stubs/java.method.name/java.method.name.storage.keystream
      Stubs/java.method.name/java.method.name.storage.keystream.len
      Stubs/java.method.name/java.method.name.storage.len
      Stubs/java.method.name/java.method.name.storage.values.at
      Stubs/java.method.name/java.method.name.storage_i
      Stubs/java.method.name/java.method.name.storage_i.len
      Stubs/serializerNames
      Stubs/serializerNames/names
      Stubs/serializerNames/names.keystream
      Stubs/serializerNames/names.keystream.len
      Stubs/serializerNames/names.len
      Stubs/serializerNames/names_i
      Stubs/serializerNames/names_i.len
      Trigram.Index
      Trigram.Index/Trigram.Index.forward
      Trigram.Index/Trigram.Index.forward.len
      Trigram.Index/Trigram.Index.forward.values.at
      Trigram.Index/Trigram.Index.forward_i
      Trigram.Index/Trigram.Index.forward_i.len
      Trigram.Index/Trigram.Index.storage
      Trigram.Index/Trigram.Index.storage.len
      Trigram.Index/Trigram.Index.storage.values.at
      Trigram.Index/Trigram.Index.storage_i
      Trigram.Index/Trigram.Index.storage_i.len
      empty-indices.txt
      empty-stub-indices.txt
      hashes
      hashes.keystream
      hashes.keystream.len
      hashes.len
      hashes_i
      hashes_i.len
      java.null.method.argument
      java.null.method.argument/java.null.method.argument.forward
      java.null.method.argument/java.null.method.argument.forward.len
      java.null.method.argument/java.null.method.argument.forward.values.at
      java.null.method.argument/java.null.method.argument.forward_i
      java.null.method.argument/java.null.method.argument.forward_i.len
      java.null.method.argument/java.null.method.argument.storage
      java.null.method.argument/java.null.method.argument.storage.keystream
      java.null.method.argument/java.null.method.argument.storage.keystream.len
      java.null.method.argument/java.null.method.argument.storage.len
      java.null.method.argument/java.null.method.argument.storage.values.at
      java.null.method.argument/java.null.method.argument.storage_i
      java.null.method.argument/java.null.method.argument.storage_i.len
      java.simple.property
      java.simple.property/java.simple.property.storage
      java.simple.property/java.simple.property.storage.len
      java.simple.property/java.simple.property.storage.values.at
      java.simple.property/java.simple.property.storage_i
      java.simple.property/java.simple.property.storage_i.len
    """.trimIndent(), actualFiles)
  }

  fun `test shared index chunk layout when version doesn't exactly match`() {
    doSharedIndexMountTest(generateTestSharedIndexChunk())
  }

  private fun doSharedIndexMountTest(indexZipPath: Path) {
    val appendStorage = tempDir.resolve("append-index.zip")

    val ideVersion = IndexInfrastructureVersion.getIdeVersion()

    val modifiedFbiIndexVersions = THashMap(ideVersion.fileBasedIndexVersions)
    assertNotNull(modifiedFbiIndexVersions.put(JavaNullMethodArgumentIndex.INDEX_ID.name, "-123"))
    assertNotNull(modifiedFbiIndexVersions.put("java.simple.property", "-321"))
    val modifiedVersion = IndexInfrastructureVersion(ideVersion.baseIndexes,
                                                     modifiedFbiIndexVersions,
                                                     ideVersion.stubIndexVersions)

    SharedIndexStorageUtil.appendToSharedIndexStorage(indexZipPath, appendStorage, object : SharedIndexChunkLocator.ChunkDescriptor {
      override fun downloadChunk(targetFile: Path, indicator: ProgressIndicator) = throw AssertionFailedError()

      override fun getOrderEntries() = throw AssertionFailedError()

      override fun getChunkUniqueId(): String = "source"

      override fun getSupportedInfrastructureVersion(): IndexInfrastructureVersion = ideVersion
    }, modifiedVersion)

    val actualFiles = UncompressedZipFileSystem.create(appendStorage).use {
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

  private fun generateTestSharedIndexChunk(): Path {
    val file = setupProject()
    val indexZipPath = tempDir.resolve("shared-index-chunk.zip")
    val chunk = IndexChunk(setOf(file), "chunk-name")

    IndexesExporter
      .getInstance(project)
      .exportIndexesChunk(chunk, indexZipPath, EmptyProgressIndicator())

    return indexZipPath
  }

  private fun setupProject(): VirtualFile? {
    return myFixture.configureByText("A.java", """
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
  }
}