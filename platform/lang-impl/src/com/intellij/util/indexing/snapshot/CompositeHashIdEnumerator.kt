// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.snapshot

import com.intellij.openapi.Forceable
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.IndexInfrastructure
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentEnumerator
import java.io.Closeable
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

private val LOG = logger<CompositeHashIdEnumerator>()

internal class CompositeHashIdEnumerator(private val indexId: ID<*, *>): Closeable, Forceable {
  @Volatile
  private var enumerator = init()

  @Throws(IOException::class)
  override fun close() = enumerator.close()

  override fun isDirty(): Boolean = enumerator.isDirty

  override fun force() = enumerator.force()

  @Throws(IOException::class)
  fun clear() {
    try {
      close()
    }
    catch (e: IOException) {
      LOG.error(e)
    }
    finally {
      IOUtil.deleteAllFilesStartingWith(getBasePath())
      init()
    }
  }

  fun enumerate(hashId: Int, subIndexerTypeId: Int) = enumerator.enumerate(CompositeHashId(hashId, subIndexerTypeId))

  private fun getBasePath() = IndexInfrastructure.getIndexRootDir(indexId).resolve("compositeHashId")

  private fun init(): PersistentEnumerator<CompositeHashId> {
    enumerator = PersistentEnumerator(getBasePath(), CompositeHashIdDescriptor(), 64 * 1024)
    return enumerator
  }
}

private data class CompositeHashId(val baseHashId: Int, val subIndexerTypeId: Int)

private class CompositeHashIdDescriptor : KeyDescriptor<CompositeHashId> {
  override fun getHashCode(value: CompositeHashId): Int {
    return value.hashCode()
  }

  override fun isEqual(val1: CompositeHashId, val2: CompositeHashId): Boolean {
    return val1 == val2
  }

  @Throws(IOException::class)
  override fun save(out: DataOutput, value: CompositeHashId) {
    DataInputOutputUtil.writeINT(out, value.baseHashId)
    DataInputOutputUtil.writeINT(out, value.subIndexerTypeId)
  }

  @Throws(IOException::class)
  override fun read(`in`: DataInput): CompositeHashId {
    return CompositeHashId(DataInputOutputUtil.readINT(`in`), DataInputOutputUtil.readINT(`in`))
  }
}