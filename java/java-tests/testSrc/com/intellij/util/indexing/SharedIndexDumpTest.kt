// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.hash.ContentHashEnumerator
import com.intellij.util.indexing.hash.building.IndexChunk
import com.intellij.util.indexing.hash.building.IndexesExporter
import com.intellij.util.indexing.snapshot.IndexedHashesSupport
import com.intellij.util.indexing.zipFs.UncompressedZipFileSystem
import com.intellij.util.indexing.zipFs.UncompressedZipFileSystemProvider
import junit.framework.TestCase
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

@SkipSlowTestLocally
class SharedIndexDumpTest : LightJavaCodeInsightFixtureTestCase() {

  fun testSharedIndexHashes() {
    val indexZipPath = generateTestSharedIndex()
    val indexFs = UncompressedZipFileSystem(indexZipPath, UncompressedZipFileSystemProvider())

    val hashEnumerator = indexFs.getPath("source", "hashes")

    val content = FileContentImpl.createByFile(myFixture.findClass("A").containingFile.virtualFile, project) as FileContentImpl
    val hash = IndexedHashesSupport.getOrInitIndexedHash(content, false)

    val hashId = ContentHashEnumerator(hashEnumerator).use {
      it.tryEnumerate(hash)
    }

    assertTrue(hashId > 0)
  }

  fun testSharedIndexLayout() {
    val indexZipPath = generateTestSharedIndex()
    val indexFs = UncompressedZipFileSystem(indexZipPath, UncompressedZipFileSystemProvider())

    val root = indexFs.rootDirectories.first()

    val actualFiles = Files.walk(root).map { it.toString() }.sorted().collect(Collectors.joining("\n")).trimStart()

    assertEquals(actualFiles, """
      source
      source/empty-indices.txt
      source/empty-stub-indices.txt
      source/hashes
      source/hashes.keystream
      source/hashes.keystream.len
      source/hashes.len
      source/hashes_i
      source/hashes_i.len
      source/idindex
      source/idindex/IdIndex.forward
      source/idindex/IdIndex.forward.len
      source/idindex/IdIndex.forward.values.at
      source/idindex/IdIndex.forward_i
      source/idindex/IdIndex.forward_i.len
      source/idindex/IdIndex.storage
      source/idindex/IdIndex.storage.len
      source/idindex/IdIndex.storage.values.at
      source/idindex/IdIndex.storage_i
      source/idindex/IdIndex.storage_i.len
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
      source/stubs
      source/stubs/Stubs.storage
      source/stubs/Stubs.storage.len
      source/stubs/Stubs.storage.values.at
      source/stubs/Stubs.storage_i
      source/stubs/Stubs.storage_i.len
      source/stubs/java.class.fqn
      source/stubs/java.class.fqn/java.class.fqn.forward
      source/stubs/java.class.fqn/java.class.fqn.forward.len
      source/stubs/java.class.fqn/java.class.fqn.storage
      source/stubs/java.class.fqn/java.class.fqn.storage.len
      source/stubs/java.class.fqn/java.class.fqn.storage.values.at
      source/stubs/java.class.fqn/java.class.fqn.storage_i
      source/stubs/java.class.fqn/java.class.fqn.storage_i.len
      source/stubs/java.class.shortname
      source/stubs/java.class.shortname/java.class.shortname.forward
      source/stubs/java.class.shortname/java.class.shortname.forward.len
      source/stubs/java.class.shortname/java.class.shortname.storage
      source/stubs/java.class.shortname/java.class.shortname.storage.keystream
      source/stubs/java.class.shortname/java.class.shortname.storage.keystream.len
      source/stubs/java.class.shortname/java.class.shortname.storage.len
      source/stubs/java.class.shortname/java.class.shortname.storage.values.at
      source/stubs/java.class.shortname/java.class.shortname.storage_i
      source/stubs/java.class.shortname/java.class.shortname.storage_i.len
      source/stubs/java.method.name
      source/stubs/java.method.name/java.method.name.forward
      source/stubs/java.method.name/java.method.name.forward.len
      source/stubs/java.method.name/java.method.name.storage
      source/stubs/java.method.name/java.method.name.storage.keystream
      source/stubs/java.method.name/java.method.name.storage.keystream.len
      source/stubs/java.method.name/java.method.name.storage.len
      source/stubs/java.method.name/java.method.name.storage.values.at
      source/stubs/java.method.name/java.method.name.storage_i
      source/stubs/java.method.name/java.method.name.storage_i.len
      source/stubs/rep.names
      source/stubs/rep.names.keystream
      source/stubs/rep.names.keystream.len
      source/stubs/rep.names.len
      source/stubs/rep.names_i
      source/stubs/rep.names_i.len
      source/trigram.index
      source/trigram.index/Trigram.Index.forward
      source/trigram.index/Trigram.Index.forward.len
      source/trigram.index/Trigram.Index.forward.values.at
      source/trigram.index/Trigram.Index.forward_i
      source/trigram.index/Trigram.Index.forward_i.len
      source/trigram.index/Trigram.Index.storage
      source/trigram.index/Trigram.Index.storage.len
      source/trigram.index/Trigram.Index.storage.values.at
      source/trigram.index/Trigram.Index.storage_i
      source/trigram.index/Trigram.Index.storage_i.len
    """.trimIndent())
  }

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
    chunks += IndexChunk(setOf(file), "source")

    IndexesExporter
      .getInstance(project)
      .exportIndices(chunks, indexZipPath, EmptyProgressIndicator())
    return indexZipPath
  }
}