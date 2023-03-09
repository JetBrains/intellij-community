// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsFileContentReader
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsFileContentWriter
import org.jdom.Element
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path

internal class AppStorageContentReader : JpsFileContentReader {
  override fun loadComponent(fileUrl: String, componentName: String, customModuleFilePath: String?): Element? {
    val filePath = JpsPathUtil.urlToPath(fileUrl)
    if (isApplicationLevelFile(filePath)) {
      val storageSpec = FileStorageAnnotation(PathUtil.getFileName(filePath), false, StateSplitterEx::class.java)
      @Suppress("UNCHECKED_CAST")
      val storage = ApplicationManager.getApplication().stateStore.storageManager.getStateStorage(storageSpec) as StateStorageBase<StateMap>
      val stateMap = storage.getStorageData()
      return stateMap.getElement(componentName)
    }
    return null
  }

  override fun getExpandMacroMap(fileUrl: String): ExpandMacroToPathMap {
    return PathMacroManager.getInstance(ApplicationManager.getApplication()).expandMacroMap
  }

  private fun isApplicationLevelFile(filePath: String): Boolean {
    return FileUtil.isAncestor(Path.of(PathManager.getOptionsPath()), Path.of(filePath), false)
  }
}

internal class AppStorageContentWriter(private val session: SaveSessionProducerManager) : JpsFileContentWriter {
  override fun saveComponent(fileUrl: String, componentName: String, componentTag: Element?) {
    val filePath = JpsPathUtil.urlToPath(fileUrl)
    if (isApplicationLevelFile(filePath)) {
      val storageSpec = FileStorageAnnotation(PathUtil.getFileName(filePath), false, StateSplitterEx::class.java)
      @Suppress("UNCHECKED_CAST")
      val storage = ApplicationManager.getApplication().stateStore.storageManager.getStateStorage(storageSpec) as StateStorageBase<StateMap>
      session.getProducer(storage)?.setState(null, componentName, componentTag)
    }
  }

  override fun getReplacePathMacroMap(fileUrl: String): PathMacroMap {
    return PathMacroManager.getInstance(ApplicationManager.getApplication()).replacePathMap
  }

  private fun isApplicationLevelFile(filePath: String): Boolean {
    return FileUtil.isAncestor(Path.of(PathManager.getOptionsPath()), Path.of(filePath), false)
  }
}

