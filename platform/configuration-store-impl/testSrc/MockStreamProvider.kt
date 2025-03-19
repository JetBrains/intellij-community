// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.util.io.basicAttributesIfExists
import com.intellij.util.io.directoryStreamIfExists
import java.io.InputStream
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream
import kotlin.io.path.isHidden
import kotlin.io.path.writeBytes

class MockStreamProvider(private val dir: Path) : StreamProvider {
  override val isExclusive = true

  override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
    dir.resolve(fileSpec).writeBytes(content)
  }

  override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
    try {
      this.dir.resolve(fileSpec).inputStream().use(consumer)
    }
    catch (_: NoSuchFileException) {
      consumer(null)
    }
    return true
  }

  override fun processChildren(path: String, roamingType: RoamingType, filter: (name: String) -> Boolean, processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean): Boolean {
    dir.resolve(path).directoryStreamIfExists({ filter(it.fileName.toString()) }) { directoryStream ->
      for (file in directoryStream) {
        val attributes = file.basicAttributesIfExists()
        if (attributes == null || attributes.isDirectory || file.isHidden()) {
          continue
        }

        // we ignore empty files as well - delete if corrupted
        if (attributes.size() == 0L) {
          file.deleteIfExists()
          continue
        }

        if (!file.inputStream().use { processor(file.fileName.toString(), it, false) }) {
          break
        }
      }
    }

    return true
  }

  override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
    dir.resolve(fileSpec).deleteIfExists()
    return true
  }
}
