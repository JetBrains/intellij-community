package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SmartList
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class MockStreamProvider(private val myBaseDir: File) : StreamProvider {
  override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
    FileUtil.writeToFile(File(myBaseDir, fileSpec), content, 0, size)
  }

  override fun read(fileSpec: String, roamingType: RoamingType): InputStream? {
    val file = File(myBaseDir, fileSpec)
    //noinspection IOResourceOpenedButNotSafelyClosed
    return if (file.exists()) FileInputStream(file) else null
  }

  private fun listSubFiles(fileSpec: String, roamingType: RoamingType): Collection<String> {
    if (roamingType !== RoamingType.DEFAULT) {
      return emptyList()
    }

    val files = File(myBaseDir, fileSpec).listFiles() ?: return emptyList()
    val names = SmartList<String>()
    for (file in files) {
      names.add(file.name)
    }
    return names
  }

  /**
   * You must close passed input stream.
   */
  override fun processChildren(path: String, roamingType: RoamingType, filter: (name: String) -> Boolean, processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean) {
    for (name in listSubFiles(path, roamingType)) {
      if (!filter(name)) {
        continue
      }

      val input = read("$path/$name", roamingType)
      if (input != null && !processor(name, input, false)) {
        break
      }
    }
  }

  override fun delete(fileSpec: String, roamingType: RoamingType) {
    FileUtil.delete(File(myBaseDir, fileSpec))
  }
}
