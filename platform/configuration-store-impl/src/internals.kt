// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.buildNsUnawareJdom
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.LineSeparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import java.io.StringReader
import java.nio.file.Path

internal const val VERSION_OPTION: String = "version"

@JvmField
internal val XML_PROLOG: ByteArray = """<?xml version="1.0" encoding="UTF-8"?>""".toByteArray()

internal data class Macro(@JvmField val key: String, @JvmField var value: Path)

@ApiStatus.Internal
interface StateGetter<S : Any> {
  fun getState(mergeInto: S? = null): S?

  fun archiveState(): S?
}

internal fun isSpecialStorage(collapsedPath: String): Boolean =
  collapsedPath == StoragePathMacros.CACHE_FILE || collapsedPath == StoragePathMacros.PRODUCT_WORKSPACE_FILE

@CalledInAny
internal suspend fun ensureFilesWritable(project: Project, files: Collection<VirtualFile>): ReadonlyStatusHandler.OperationStatus =
  withContext(Dispatchers.EDT) {
    ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files)
  }

internal fun loadDataAndDetectLineSeparator(file: Path): Pair<Element, LineSeparator?> {
  val text = ComponentStorageUtil.loadTextContent(file)
  return buildNsUnawareJdom(StringReader(text)) to detectLineSeparator(text)
}

private fun detectLineSeparator(chars: CharSequence): LineSeparator? {
  for (element in chars) {
    if (element == '\r') {
      return LineSeparator.CRLF
    }
    // if we are here, there was no '\r' before
    if (element == '\n') {
      return LineSeparator.LF
    }
  }
  return null
}

@ApiStatus.Internal
fun removeMacroIfStartsWith(path: String, macro: String): String = path.removePrefix("$macro/")

@Suppress("DEPRECATION", "removal")
internal val Storage.path: String
  get() = value.ifEmpty { file }

internal val useBackgroundSave: Boolean
  get() = Registry.`is`("ide.background.save.settings", false)
