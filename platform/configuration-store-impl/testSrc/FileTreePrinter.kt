package com.intellij.configurationStore

import com.intellij.util.containers.nullize
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.isDirectory
import com.intellij.util.io.readChars
import java.nio.file.Path
import java.util.*

fun printDirectoryTree(dir: Path, excluded: Set<String> = emptySet()): String {
  val sb = StringBuilder()
  printDirectoryTree(dir, 0, sb, excluded)
  return sb.toString()
}

private fun printDirectoryTree(dir: Path, indent: Int, sb: StringBuilder, excluded: Set<String>) {
  val fileList = sortedFileList(dir)?.filter { !excluded.contains(it.fileName.toString()) }.nullize() ?: return

  getIndentString(indent, sb)
  sb.append("\u251c\u2500\u2500")
  sb.append(dir.fileName.toString())
  sb.append("/")
  sb.append("\n")
  for (file in fileList) {
    if (file.isDirectory()) {
      printDirectoryTree(file, indent + 1, sb, excluded)
    }
    else {
      printFile(file, indent + 1, sb)
    }
  }
}

private fun sortedFileList(dir: Path): List<Path>? {
  return dir.directoryStreamIfExists {
    val list = ArrayList<Path>()
    it.mapTo(list) { it }
    list.sort()
    list
  }
}

private fun printFile(file: Path, indent: Int, sb: StringBuilder) {
  getIndentString(indent, sb)
  sb.append("\u251c\u2500\u2500")
  sb.append(file.fileName.toString())
  sb.append("\n")
  sb.append(file.readChars()).append("\n\n")
}

private fun getIndentString(indent: Int, sb: StringBuilder) {
  for (i in 0..indent - 1) {
    sb.append("  ")
  }
}