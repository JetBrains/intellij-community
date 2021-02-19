// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProviderBean
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.inputStream
import java.io.DataInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.outputStream

internal object FileBasedIndexLayoutSettings {
  private val log = logger<FileBasedIndexLayoutSettings>()

  @Synchronized
  fun setUsedLayout(bean: FileBasedIndexLayoutProviderBean?) {
    try {
      Files.createDirectories(indexLayoutSettingFile().parent)
      if (bean == null) {
        FileUtil.delete(indexLayoutSettingFile())
        return
      }
      DataOutputStream(indexLayoutSettingFile().outputStream().buffered()).use {
        EnumeratorStringDescriptor.INSTANCE.save(it, bean.id)
        DataInputOutputUtil.writeINT(it, bean.version)
      }
    }
    catch (e: IOException) {
      log.error(e)
    }
  }

  @Synchronized
  fun loadUsedLayout(): Boolean {
    if (!Files.exists(indexLayoutSettingFile())) {
      currentLayout = Ref.create()
      return false
    }
    else {
      val id: String
      val version: Int
      DataInputStream(indexLayoutSettingFile().inputStream().buffered()).use {
        id = EnumeratorStringDescriptor.INSTANCE.read(it)
        version = DataInputOutputUtil.readINT(it)
      }

      // scan for exact layout id & version match
      for (bean in FileBasedIndexLayoutProvider.STORAGE_LAYOUT_EP_NAME.extensionList) {
        if (bean.id == id && bean.version == version) {
          currentLayout = Ref.create(bean)
          return false
        }
      }

      // scan only matched id
      for (bean in FileBasedIndexLayoutProvider.STORAGE_LAYOUT_EP_NAME.extensionList) {
        if (bean.id == id) {
          setUsedLayout(bean)
          currentLayout = Ref.create(bean)
          return true
        }
      }

      // fallback to default
      setUsedLayout(null)
      return true
    }
  }

  @Synchronized
  fun saveCurrentLayout() {
    setUsedLayout(getUsedLayout())
  }

  @Synchronized
  fun getUsedLayout(): FileBasedIndexLayoutProviderBean? {
    val layout = currentLayout ?: throw IllegalStateException("File-based index layout settings not loaded yet")
    return layout.get()
  }

  @Synchronized
  fun resetUsedLayout() {
    currentLayout = null
  }

  private var currentLayout : Ref<FileBasedIndexLayoutProviderBean?>? = null
  private fun indexLayoutSettingFile(): Path = PathManager.getIndexRoot().resolve("indices.layout")

}