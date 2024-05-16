// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProviderBean
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.EnumeratorStringDescriptor
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.DataInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

/**
 * Persistence for custom indexLayout, if switched from default one by e.g.
 * [com.intellij.util.indexing.impl.storage.SwitchFileBasedIndexStorageAction]
 *
 * Custom layout is stored in `{indexRoot}/indices.layout` file with binary format:
 * ```
 * <layout.id: utf8><layout.version: varint>
 * ```
 *
 * This settings should be seen **as a 'hack'**, not a part of regular (release-level) IDE functionality. In a regular
 * operation it should be no 'custom layout' -- index storage layout is determined by available providers, sorted by
 * .priority (see [IndexStorageLayoutLocator]). Custom layout is a tool for the developers, to try out new/experimental
 * layouts, and to be able to manage those experimental layouts without alternating `Indexing.xml` or alike.
 * Hence, this class should have as little usages as it possible across a codebase.
 */
@Internal
object IndexLayoutPersistentSettings {

  const val INDICES_LAYOUT_FILE = "indices.layout"

  /**
   * `null` means custom layout wasn't set/loaded yet.
   * `Ref(null)` means custom layout was set/loaded, but is 'not defined' -- i.e. default layout should be used instead.
   */
  private var currentLayout: Ref<FileBasedIndexLayoutProviderBean?>? = null


  @Synchronized
  fun getCustomLayout(): FileBasedIndexLayoutProviderBean? {
    if (currentLayout == null) {
      currentLayout = Ref.create(loadLayout())
    }
    return currentLayout!!.get()
  }

  @Synchronized
  fun setCustomLayout(bean: FileBasedIndexLayoutProviderBean?) {
    currentLayout = Ref.create(bean)
    saveLayout(bean)
  }

  /**
   * Force save current layout.
   * Usually one doesn't need to call this method explicitly, since [setCustomLayout] saves newly installed custom layout anyway.
   * But sometimes it is useful if you know the file was deleted by third-party, and you want to force its re-creation.
   */
  @Synchronized
  fun forceSaveCurrentLayout() {
    saveLayout(getCustomLayout())
  }
}

private val LOG = logger<IndexLayoutPersistentSettings>()

private fun indexLayoutSettingFile(): Path = PathManager.getIndexRoot().resolve(IndexLayoutPersistentSettings.INDICES_LAYOUT_FILE)

@Synchronized
private fun loadLayout(): FileBasedIndexLayoutProviderBean? {
  val indexLayoutSettingFile = indexLayoutSettingFile()
  if (!Files.exists(indexLayoutSettingFile)) {
    return null
  }
  else {
    val id: String
    val version: Int
    DataInputStream(indexLayoutSettingFile.inputStream().buffered()).use {
      id = EnumeratorStringDescriptor.INSTANCE.read(it)
      version = DataInputOutputUtil.readINT(it)
    }

    // scan for exact layout id & version match
    for (bean in IndexStorageLayoutLocator.supportedLayoutProviders) {
      if (bean.id == id && bean.version == version) {
        return bean
      }
    }
    //We load custom-layout only if both (id & version) match -- if version doesn't match we
    // reset custom layout to null (='not set').

    // fallback to default:
    return null
  }
}

@Synchronized
private fun saveLayout(bean: FileBasedIndexLayoutProviderBean?) {
  try {
    val indexLayoutSettingFile = indexLayoutSettingFile()

    Files.createDirectories(indexLayoutSettingFile.parent)

    if (bean == null) {
      FileUtil.delete(indexLayoutSettingFile)
      return
    }

    DataOutputStream(indexLayoutSettingFile.outputStream().buffered()).use {
      EnumeratorStringDescriptor.INSTANCE.save(it, bean.id)
      DataInputOutputUtil.writeINT(it, bean.version)
    }
  }
  catch (e: IOException) {
    LOG.error(e)
  }
}
