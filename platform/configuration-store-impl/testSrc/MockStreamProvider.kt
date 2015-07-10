package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.impl.stores.StreamProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SmartList
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class MockStreamProvider(private val myBaseDir: File) : StreamProvider {
  override fun isApplicable(fileSpec: String, roamingType: RoamingType) = roamingType === RoamingType.PER_USER

  override fun saveContent(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
    FileUtil.writeToFile(File(myBaseDir, fileSpec), content, 0, size)
  }

  override fun loadContent(fileSpec: String, roamingType: RoamingType): InputStream? {
    val file = File(myBaseDir, fileSpec)
    //noinspection IOResourceOpenedButNotSafelyClosed
    return if (file.exists()) FileInputStream(file) else null
  }

  override fun listSubFiles(fileSpec: String, roamingType: RoamingType): Collection<String> {
    if (roamingType !== RoamingType.PER_USER) {
      return emptyList()
    }

    val files = File(myBaseDir, fileSpec).listFiles() ?: return emptyList()
    val names = SmartList<String>()
    for (file in files) {
      names.add(file.getName())
    }
    return names
  }

  override fun delete(fileSpec: String, roamingType: RoamingType) {
    FileUtil.delete(File(myBaseDir, fileSpec))
  }
}
