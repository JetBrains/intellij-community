// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.intellij.util.io.write
import net.jqwik.api.*
import net.jqwik.api.lifecycle.AfterProperty
import net.jqwik.api.lifecycle.BeforeProperty
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively

class HashStampStorageFuzzTest {
  private lateinit var hashStampStorage: HashStampStorage
  private lateinit var storageManager: StorageManager
  private var file: Path? = null

  private lateinit var fs: FileSystem

  @BeforeProperty
  fun setUp() {
    file = Files.createTempFile("mvstore", ".db")
    storageManager = StorageManager(file!!)
    hashStampStorage = HashStampStorage.createSourceToStampMap(
      storageManager = storageManager,
      relativizer = TestPathTypeAwareRelativizer,
      targetId = "test-module",
      targetTypeId = "java"
    )

    fs = MemoryFileSystemBuilder.newLinux().setCurrentWorkingDirectory("/").build()
  }

  @AfterProperty
  fun tearDown() {
    storageManager.close()
    file?.deleteIfExists()

    fs.close()
  }

  @Provide
  fun paths(): Arbitrary<String> {
    return Arbitraries.strings().alpha().numeric().withChars('/').ofMinLength(2).ofMaxLength(255)
  }

  @Provide
  fun hashStamps(): Arbitrary<HashStamp> {
    return Arbitraries.longs().map { HashStamp(hash = it, timestamp = System.currentTimeMillis()) }
  }

  @Property
  fun saveAndRetrieveStamp(@ForAll("paths") pathStr: String, @ForAll("hashStamps") stamp: HashStamp) {
    val file = prepareAndGetFile(pathStr)

    hashStampStorage.updateStamp(file, null, stamp.timestamp)
    val retrievedStamp = hashStampStorage.getStoredFileStamp(file)
    assertThat(retrievedStamp).isNotNull()
    assertThat(retrievedStamp!!.timestamp).isEqualTo(stamp.timestamp)
  }

  @OptIn(ExperimentalPathApi::class)
  private fun prepareAndGetFile(pathStr: String): Path {
    val file = fs.getPath("/test", pathStr.replace("//", "/").let { if (it.length == 1) "/test" else it })
    file.parent.deleteRecursively()
    file.write("")
    return file
  }

  @Property
  fun removeAndCheckStamp(@ForAll("paths") pathStr: String) {
    val file = prepareAndGetFile(pathStr)

    val stamp = HashStamp(hash = 12345L, timestamp = System.currentTimeMillis())
    hashStampStorage.updateStamp(file, null, stamp.timestamp)
    hashStampStorage.removeStamp(file, null)
    val retrievedStamp = hashStampStorage.getStoredFileStamp(file)
    assertThat(retrievedStamp).isNull()
  }
}