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

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileTypeExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

/**
 * @author traff
 */

val EP_NAME = "com.intellij.filetype.prebuiltStubsProvider"

object PrebuiltStubsProviders : FileTypeExtension<PrebuiltStubsProvider>(EP_NAME) {
  val EXTENSION_POINT_NAME = ExtensionPointName.create<BinaryFileStubBuilder>(EP_NAME)
}

interface PrebuiltStubsProvider {
  fun findStub(fileContent: FileContent): Stub?
}

class FileContentHashing {
  private val hashing = Hashing.sha256()

  fun hashString(fileContent: FileContent): HashCode {
    return hashing.hashBytes(fileContent.content)
  }
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