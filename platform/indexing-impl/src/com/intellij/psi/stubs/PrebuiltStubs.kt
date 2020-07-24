// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.intellij.index.PrebuiltIndexProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypeExtension
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.indexing.FileContent
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentHashMap
import java.io.DataInput
import java.io.DataOutput
import java.io.File
import java.nio.file.Files

const val EP_NAME = "com.intellij.filetype.prebuiltStubsProvider"

val prebuiltStubsProvider: FileTypeExtension<PrebuiltStubsProvider> =
  FileTypeExtension<PrebuiltStubsProvider>(EP_NAME)

interface PrebuiltStubsProvider {
  /**
   * Tries to find stub for [fileContent] in this provider.
   */
  fun findStub(fileContent: FileContent): SerializedStubTree?
}

class FileContentHashing {
  private val hashing = Hashing.sha256()

  fun hashString(fileContent: FileContent): HashCode = hashing.hashBytes(fileContent.content)!!
}

class HashCodeDescriptor : HashCodeExternalizers(), KeyDescriptor<HashCode> {
  override fun getHashCode(value: HashCode): Int = value.hashCode()

  override fun isEqual(val1: HashCode, val2: HashCode): Boolean = val1 == val2

  companion object {
    val instance: HashCodeDescriptor = HashCodeDescriptor()
  }
}

open class HashCodeExternalizers : DataExternalizer<HashCode> {
  override fun save(out: DataOutput, value: HashCode) {
    val bytes = value.asBytes()
    DataInputOutputUtil.writeINT(out, bytes.size)
    out.write(bytes, 0, bytes.size)
  }

  override fun read(`in`: DataInput): HashCode {
    val len = DataInputOutputUtil.readINT(`in`)
    val bytes = ByteArray(len)
    `in`.readFully(bytes)
    return HashCode.fromBytes(bytes)
  }
}

class GeneratingFullStubExternalizer : SerializedStubTreeDataExternalizer(true,
                                                                          SerializationManagerEx.getInstanceEx(),
                                                                          StubForwardIndexExternalizer.createFileLocalExternalizer())

abstract class PrebuiltStubsProviderBase : PrebuiltIndexProvider<SerializedStubTree>(), PrebuiltStubsProvider {
  private lateinit var mySerializationManager: SerializationManagerImpl

  protected abstract val stubVersion: Int

  override val indexName: String get() = SDK_STUBS_STORAGE_NAME

  override val indexExternalizer: SerializedStubTreeDataExternalizer get() = SerializedStubTreeDataExternalizer(true, mySerializationManager, StubForwardIndexExternalizer.createFileLocalExternalizer())

  companion object {
    const val PREBUILT_INDICES_PATH_PROPERTY = "prebuilt_indices_path"
    const val SDK_STUBS_STORAGE_NAME = "sdk-stubs"
    private val LOG = logger<PrebuiltStubsProviderBase>()
  }

  protected open fun getIndexVersion(): Int = -1

  override fun openIndexStorage(indexesRoot: File): PersistentHashMap<HashCode, SerializedStubTree>? {
    val formatFile = indexesRoot.toPath().resolve("$indexName.version")
    val versionFileText = Files.readAllLines(formatFile)
    if (versionFileText.size != 2) {
      LOG.warn("Invalid version file format: \"$versionFileText\" (file=$formatFile)")
      return null
    }

    val stubSerializationVersion = versionFileText[0]
    val currentSerializationVersion = StringUtilRt.parseInt(stubSerializationVersion, 0)
    val expected = getIndexVersion()
    if (expected != -1 && currentSerializationVersion != expected) {
      LOG.error("Stub serialization version mismatch: $expected, current version is $currentSerializationVersion")
      return null
    }
    val prebuiltIndexVersion = versionFileText[1]
    if (StringUtilRt.parseInt(prebuiltIndexVersion, 0) != stubVersion) {
      LOG.error("Prebuilt stubs version mismatch: $prebuiltIndexVersion, current version is $stubVersion")
      return null
    }
    else {
      mySerializationManager = SerializationManagerImpl(File(indexesRoot, "$indexName.names").toPath(), true)
      Disposer.register(ApplicationManager.getApplication(), mySerializationManager)
      return super.openIndexStorage(indexesRoot)
    }
  }


  override fun findStub(fileContent: FileContent): SerializedStubTree? {
    try {
      return get(fileContent)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      dispose()
      LOG.error("Can't get prebuilt stub tree from ${this.javaClass.name}", e)
    }
    return null
  }
}
