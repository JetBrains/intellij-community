package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.util.io.*
import java.io.InputStream
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class MockStreamProvider(private val dir: Path) : StreamProvider {
  override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
    dir.resolve(fileSpec).write(content, 0, size)
  }

  override fun read(fileSpec: String, roamingType: RoamingType): InputStream? {
    val file = dir.resolve(fileSpec)
    try {
      return file.inputStream()
    }
    catch (e: NoSuchFileException) {
      return null
    }
  }

  override fun processChildren(path: String, roamingType: RoamingType, filter: (name: String) -> Boolean, processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean) {
    dir.resolve(path).directoryStreamIfExists({ filter(it.fileName.toString()) }) {
      for (file in it) {
        val attributes = file.basicAttributesIfExists()
        if (attributes == null || attributes.isDirectory || file.isHidden()) {
          continue
        }

        // we ignore empty files as well - delete if corrupted
        if (attributes.size() == 0L) {
          file.delete()
          continue
        }

        if (!file.inputStream().use { processor(file.fileName.toString(), it, false) }) {
          break
        }
      }
    }
  }

  override fun delete(fileSpec: String, roamingType: RoamingType) {
    dir.resolve(fileSpec).delete()
  }
}
