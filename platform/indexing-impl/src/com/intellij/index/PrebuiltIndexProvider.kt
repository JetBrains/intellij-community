// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.index

import com.google.common.hash.HashCode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.stubs.FileContentHashing
import com.intellij.psi.stubs.HashCodeDescriptor
import com.intellij.psi.stubs.PrebuiltStubsProviderBase
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.FileContent
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.PersistentHashMap
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock


abstract class PrebuiltIndexProvider<Value>: Disposable {
  private val myFileContentHashing = FileContentHashing()
  private val myOpenCloseLock = ReentrantReadWriteLock()
  private var myPrebuiltIndexStorage: PersistentHashMap<HashCode, Value>? = null

  protected abstract val dirName: String
  protected abstract val indexName: String
  protected abstract val indexExternalizer: DataExternalizer<Value>

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.index.PrebuiltIndexProviderBase")

    @JvmField
    val DEBUG_PREBUILT_INDICES: Boolean = SystemProperties.getBooleanProperty("debug.prebuilt.indices", false)

    @JvmField
    val USE_PREBUILT_INDEX = Registry.`is`("use.prebuilt.indices")
  }

  init {
    init()
  }

  internal fun init() {
    myOpenCloseLock.writeLock().withLock {
      if (USE_PREBUILT_INDEX) {
        val indexesRoot = findPrebuiltIndicesRoot()
        try {
          if (indexesRoot != null && indexesRoot.exists()) {
            myPrebuiltIndexStorage = openIndexStorage(indexesRoot)
            LOG.info("Using prebuilt $indexName from " + myPrebuiltIndexStorage?.baseFile?.toAbsolutePath())
          }
          else {
            LOG.info("Prebuilt $indexName indices are missing for $dirName")
          }
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: Exception) {
          myPrebuiltIndexStorage = null
          LOG.warn("Prebuilt indices can't be loaded at " + indexesRoot!!, e)
        }
      }
    }
  }

  protected fun get(fileContent: FileContent): Value? {
    var corrupted = false

    myOpenCloseLock.readLock().withLock {
      if (myPrebuiltIndexStorage != null) {
        val hashCode = myFileContentHashing.hashString(fileContent)
        try {
          return myPrebuiltIndexStorage!!.get(hashCode)
        }
        catch (e: Exception) {
          LOG.error("Error reading prebuilt stubs from " + myPrebuiltIndexStorage!!.baseFile, e)
          corrupted = true
        }
      }
    }

    if (corrupted) {
      doDispose()
    }

    return null
  }

  open fun openIndexStorage(indexesRoot: File): PersistentHashMap<HashCode, Value>? {
    return object : PersistentHashMap<HashCode, Value>(
      File(indexesRoot, "$indexName.input"),
      HashCodeDescriptor.instance,
      indexExternalizer) {
      override fun isReadOnly(): Boolean {
        return true
      }
    }
  }

  protected abstract fun getIndexRoot(): File

  private fun findPrebuiltIndicesRoot(): File? {
    val path: String? = System.getProperty(PrebuiltStubsProviderBase.PREBUILT_INDICES_PATH_PROPERTY)
    if (path != null && File(path).exists()) {
      return File(path, dirName)
    }
    val f = indexRoot()
    return if (f.exists()) f else null
  }

  open fun indexRoot(): File = File(PathManager.getHomePath(), "index/$dirName") // compiled binary

  override fun dispose() {
    doDispose()
  }

  private fun doDispose() {
    myOpenCloseLock.writeLock().withLock {
      if (myPrebuiltIndexStorage != null) {
        try {
          myPrebuiltIndexStorage!!.close()
        }
        catch (e: IOException) {
          LOG.error(e)
        }
        myPrebuiltIndexStorage = null
      }
    }
  }

}
