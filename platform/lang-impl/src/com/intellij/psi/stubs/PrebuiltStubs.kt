/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.stubs

import com.google.common.annotations.VisibleForTesting
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeExtension
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.IndexInfrastructure
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentHashMap
import org.jetbrains.annotations.ApiStatus
import java.io.DataInput
import java.io.DataOutput
import java.io.File
import java.io.IOException

/**
 * @author traff
 */

val EP_NAME = "com.intellij.filetype.prebuiltStubsProvider"

object PrebuiltStubsProviders : FileTypeExtension<PrebuiltStubsProvider>(EP_NAME)

@ApiStatus.Experimental
interface PrebuiltStubsProvider {
  fun findStub(fileContent: FileContent): Stub?
}

class FileContentHashing {
  private val hashing = Hashing.sha256()

  fun hashString(fileContent: FileContent) = hashing.hashBytes(fileContent.content)
}


class HashCodeDescriptor : HashCodeExternalizers(), KeyDescriptor<HashCode> {
  override fun getHashCode(value: HashCode): Int = value.hashCode()

  override fun isEqual(val1: HashCode, val2: HashCode): Boolean = val1 == val2

  companion object {
    val instance = HashCodeDescriptor()
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

  override fun read(`in`: DataInput) = SerializedStubTree(`in`)
}

abstract class PrebuiltStubsProviderBase : PrebuiltStubsProvider, Disposable {
  private val myFileContentHashing = FileContentHashing()
  private var myPrebuiltStubsStorage: PersistentHashMap<HashCode, SerializedStubTree>? = null
  private var mySerializationManager: SerializationManagerImpl? = null

  protected abstract val stubVersion: Int
  protected abstract val name: String

  init {
    init()
  }

  companion object {
    val PREBUILT_INDICES_PATH_PROPERTY = "prebuilt_indices_path"
    val SDK_STUBS_STORAGE_NAME = "sdk-stubs"
    private val LOG = Logger.getInstance("#com.jetbrains.python.psi.impl.stubs.PyPrebuiltStubsProviderBase")
  }

  @VisibleForTesting
  internal fun init() {
    var indexesRoot = findPrebuiltIndicesRoot()
    try {
      if (indexesRoot != null) {
        // we should copy prebuilt indexes to a writable folder
        indexesRoot = copyPrebuiltIndexesToIndexRoot(indexesRoot)
        // otherwise we can get access denied error, because persistent hash map opens file for read and write

        val versionInFile = FileUtil.loadFile(File(indexesRoot, SDK_STUBS_STORAGE_NAME + ".version"))

        if (Integer.parseInt(versionInFile) == stubVersion) {
          myPrebuiltStubsStorage = object : PersistentHashMap<HashCode, SerializedStubTree>(
            File(indexesRoot, SDK_STUBS_STORAGE_NAME + ".input"),
            HashCodeDescriptor.instance,
            StubTreeExternalizer()) {
            override fun isReadOnly(): Boolean {
              return true
            }
          }

          mySerializationManager = SerializationManagerImpl(File(indexesRoot, SDK_STUBS_STORAGE_NAME + ".names"))

          Disposer.register(this, mySerializationManager!!)

          LOG.info("Using prebuilt stubs from " + myPrebuiltStubsStorage!!.baseFile.absolutePath)
        }
        else {
          LOG.error("Prebuilt stubs version mismatch: $versionInFile, current version is $versionInFile")
        }
      }
    }
    catch (e: Exception) {
      myPrebuiltStubsStorage = null
      LOG.warn("Prebuilt stubs can't be loaded at " + indexesRoot!!, e)
    }

  }

  override fun findStub(fileContent: FileContent): Stub? {
    if (myPrebuiltStubsStorage != null) {
      val hashCode = myFileContentHashing.hashString(fileContent)
      var stub: Stub? = null
      try {
        val stubTree = myPrebuiltStubsStorage!!.get(hashCode)
        if (stubTree != null) {
          stub = stubTree.getStub(false, mySerializationManager!!)
        }
      }
      catch (e: SerializerNotFoundException) {
        LOG.error("Can't deserialize stub tree", e)
      }
      catch (e: Exception) {
        LOG.error("Error reading prebuilt stubs from " + myPrebuiltStubsStorage!!.baseFile.path, e)
        myPrebuiltStubsStorage = null
        stub = null
      }

      if (stub != null) {
        return stub
      }
    }
    return null
  }

  override fun dispose() {
    if (myPrebuiltStubsStorage != null) {
      try {
        myPrebuiltStubsStorage!!.close()
      }
      catch (e: IOException) {
        LOG.error(e)
      }
    }
  }

  @Throws(IOException::class)
  private fun copyPrebuiltIndexesToIndexRoot(prebuiltIndicesRoot: File): File {
    val indexRoot = File(IndexInfrastructure.getPersistentIndexRoot(), "prebuilt/" + name)

    FileUtil.copyDir(prebuiltIndicesRoot, indexRoot)

    return indexRoot
  }

  private fun findPrebuiltIndicesRoot(): File? {
    var path: String? = System.getProperty(PREBUILT_INDICES_PATH_PROPERTY)
    if (path != null && File(path).exists()) {
      return File(path)
    }
    path = PathManager.getHomePath()
    val f = File(path, "index/$name") // compiled binary
    return if (f.exists()) f else null
  }
}