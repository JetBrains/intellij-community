// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.intellij.index.PrebuiltIndexProviderBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeExtension
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.indexing.IndexingDataKeys
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.DataInput
import java.io.DataOutput
import java.io.File

/**
 * @author traff
 */

const val EP_NAME: String = "com.intellij.filetype.prebuiltStubsProvider"

object PrebuiltStubsProviders : FileTypeExtension<PrebuiltStubsProvider>(EP_NAME)

fun findStub(virtualFile: VirtualFile, project: Project): Stub? {
  return PrebuiltStubsProviders
    .allForFileType(virtualFile.fileType)
    .filter { it.isEnabled(project) }
    .mapNotNull { it.findStub(FileContentImpl(virtualFile, virtualFile.contentsToByteArray()).also { c ->
      c.putUserData(IndexingDataKeys.PROJECT, project)
    }) }
    .firstOrNull()
}

fun findStub(fileContent: FileContent): Stub? {
  val rootStub = PrebuiltStubsProviders
    .allForFileType(fileContent.fileType)
    .filter { it.isEnabled(fileContent.project) }
    .mapNotNull { it.findStub(fileContent) }
    .firstOrNull()

  if (PrebuiltIndexProviderBase.DEBUG_PREBUILT_INDICES) {
    val stub = StubTreeBuilder.buildStubTree(fileContent)
    if (rootStub != null && stub != null) {
      StubUpdatingIndex.check(rootStub, stub)
    }
  }
  return rootStub
}

@ApiStatus.Experimental
interface PrebuiltStubsProvider {
  fun isEnabled(project: Project): Boolean

  fun findStub(fileContent: FileContent): Stub?
}

class FileContentHashing {
  private val hashing = Hashing.sha256()

  fun hashString(fileContent: FileContent): HashCode = hashing.hashBytes(fileContent.content)!!
}


class HashCodeDescriptor : HashCodeExternalizers(), KeyDescriptor<HashCode> {
  override fun getHashCode(value: HashCode): Int = value.hashCode()

  override fun isEqual(val1: HashCode, val2: HashCode): Boolean = val1 == val2

  companion object {
    @JvmField
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

class StubTreeExternalizer : DataExternalizer<SerializedStubTree> {
  override fun save(out: DataOutput, value: SerializedStubTree) {
    value.write(out)
  }

  override fun read(`in`: DataInput): SerializedStubTree = SerializedStubTree(`in`)
}

abstract class PrebuiltStubsProviderBase : PrebuiltIndexProviderBase<SerializedStubTree>(), PrebuiltStubsProvider {
  private var mySerializationManager: SerializationManagerImpl? = null

  protected abstract val stubVersion: Int

  override val indexName: String get() = SDK_STUBS_STORAGE_NAME

  override val indexExternalizer: StubTreeExternalizer get() = StubTreeExternalizer()

  override fun isEnabled(project: Project) = Registry.`is`("use.bundled.prebuilt.indices")

  companion object {
    const val PREBUILT_INDICES_PATH_PROPERTY: String = "prebuilt_indices_path"
    const val SDK_STUBS_STORAGE_NAME: String = "sdk-stubs"
    private val LOG = Logger.getInstance("#com.intellij.psi.stubs.PrebuiltStubsProviderBase")
  }

  override fun openIndexStorage(indexesRoot: File): PersistentHashMap<HashCode, SerializedStubTree>? {
    val versionInFile = FileUtil.loadFile(File(indexesRoot, "$indexName.version"))

    return if (Integer.parseInt(versionInFile) == stubVersion) {
      mySerializationManager = SerializationManagerImpl(File(indexesRoot, "$indexName.names"))

      Disposer.register(ApplicationManager.getApplication(), mySerializationManager!!)

      super.openIndexStorage(indexesRoot)
    }
    else {
      LOG.error("Prebuilt stubs version mismatch: $versionInFile, current version is $stubVersion")
      null
    }
  }

  override fun findStub(fileContent: FileContent): Stub? {
    var stub: Stub? = null
    try {
      val stubTree = get(fileContent)
      if (stubTree != null) {
        stub = stubTree.getStub(false, mySerializationManager!!)
      }
    }
    catch (e: SerializerNotFoundException) {
      LOG.error("Can't deserialize stub tree", e)
    }

    if (stub != null) {
      return stub
    }
    return null
  }
}

@TestOnly
fun PrebuiltStubsProviderBase.reset() {
  this.init()
}