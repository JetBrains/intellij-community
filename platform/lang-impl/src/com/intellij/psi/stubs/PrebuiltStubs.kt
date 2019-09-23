// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.intellij.index.PrebuiltIndexProviderBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeExtension
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.psi.PsiElement
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentHashMap
import org.jetbrains.annotations.TestOnly
import java.io.DataInput
import java.io.DataOutput
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.function.UnaryOperator

const val EP_NAME = "com.intellij.filetype.prebuiltStubsProvider"

object PrebuiltStubsProviders : FileTypeExtension<PrebuiltStubsProvider>(EP_NAME)

interface PrebuiltStubsProvider {
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

class FullStubExternalizer : SerializedStubTreeDataExternalizer()

val FILE_LOCAL_STUB_FORWARD_INDEX_EXTERNALIZER: StubForwardIndexExternalizer<*> = FileLocalStubForwardIndexExternalizer()

private class FileLocalStubForwardIndexExternalizer : StubForwardIndexExternalizer<FileLocalStringEnumerator>() {
  override fun createStubIndexKeySerializationState(out: DataOutput,
                                                    set: MutableSet<StubIndexKey<Any, PsiElement>>): FileLocalStringEnumerator {
    val enumerator = FileLocalStringEnumerator(true)
    set.map { it.name }.forEach { enumerator.enumerate(it)}
    enumerator.write(out)
    return enumerator
  }

  override fun writeStubIndexKey(out: DataOutput, key: StubIndexKey<*, *>, state: FileLocalStringEnumerator?) {
    DataInputOutputUtil.writeINT(out, state!!.enumerate(key.name))
  }

  override fun createStubIndexKeySerializationState(input: DataInput, stubIndexKeyCount: Int): FileLocalStringEnumerator {
    val enumerator = FileLocalStringEnumerator(false)
    FileLocalStringEnumerator.readEnumeratedStrings(enumerator, input, UnaryOperator.identity())
    return enumerator
  }

  override fun readStubIndexKey(input: DataInput, stubKeySerializationState: FileLocalStringEnumerator?): ID<*, *> {
    return ID.findByName<Any, Any>(stubKeySerializationState!!.valueOf(DataInputOutputUtil.readINT(input))!!)!!
  }
}

abstract class PrebuiltStubsProviderBase : PrebuiltIndexProviderBase<SerializedStubTree>(), PrebuiltStubsProvider {

  private var mySerializationManager: SerializationManagerImpl? = null
  private val myIdeSerializationManager = SerializationManager.getInstance() as SerializationManagerImpl
  private val myIndexedStubsSerializer = FileLocalStubForwardIndexExternalizer()

  protected abstract val stubVersion: Int

  override val indexName: String get() = SDK_STUBS_STORAGE_NAME

  override val indexExternalizer: FullStubExternalizer get() = FullStubExternalizer()

  companion object {
    const val PREBUILT_INDICES_PATH_PROPERTY: String = "prebuilt_indices_path"
    const val SDK_STUBS_STORAGE_NAME: String = "sdk-stubs"
    private val LOG = Logger.getInstance("#com.intellij.psi.stubs.PrebuiltStubsProviderBase")
  }

  override fun openIndexStorage(indexesRoot: File): PersistentHashMap<HashCode, SerializedStubTree>? {
    val formatFile = indexesRoot.toPath().resolve("$indexName.version")
    val versionFileText = Files.readAllLines(formatFile)
    if (versionFileText.size != 2) {
      LOG.warn("Invalid version file format: \"$versionFileText\" (file=$formatFile)")
      return null
    }

    val stubSerializationVersion = versionFileText[0]
    val currentSerializationVersion = StringUtilRt.parseInt(stubSerializationVersion, 0)
    val expected = FileBasedIndexExtension.EXTENSION_POINT_NAME.findExtension(StubUpdatingIndex::class.java)!!.version
    if (currentSerializationVersion != expected) {
      LOG.error("Stub serialization version mismatch: $expected, current version is $currentSerializationVersion")
      return null
    }
    val prebuiltIndexVersion = versionFileText[1]
    if (StringUtilRt.parseInt(prebuiltIndexVersion, 0) != stubVersion) {
      LOG.error("Prebuilt stubs version mismatch: $prebuiltIndexVersion, current version is $stubVersion")
      return null
    }
    else {
      mySerializationManager = SerializationManagerImpl(File(indexesRoot, "$indexName.names"), true)
      Disposer.register(ApplicationManager.getApplication(), mySerializationManager!!)
      return super.openIndexStorage(indexesRoot)
    }
  }


  override fun findStub(fileContent: FileContent): SerializedStubTree? {
    try {
      val stubTree = get(fileContent)
      if (stubTree != null) {
        return stubTree.reSerialize(mySerializationManager!!, myIdeSerializationManager, myIndexedStubsSerializer, StubForwardIndexExternalizer.IdeStubForwardIndexesExternalizer.INSTANCE)
      }
    }
    catch (e: IOException) {
      LOG.error("Can't re-serialize stub tree", e)
    }
    return null
  }
}

@TestOnly
fun PrebuiltStubsProviderBase.reset(): Boolean = this.init()
