package com.intellij.configurationStore

import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.isDirectory
import java.nio.file.Path
import java.util.*

fun printDirectoryTree(dir: Path): String {
  val sb = StringBuilder()
  printDirectoryTree(dir, 0, sb)
  return sb.toString()
}

private fun printDirectoryTree(dir: Path, indent: Int, sb: StringBuilder) {
  getIndentString(indent, sb)
  sb.append("\u251c\u2500\u2500")
  sb.append(dir.fileName.toString())
  sb.append("/")
  sb.append("\n")
  for (file in sortedFileList(dir)) {
    if (file.isDirectory()) {
      printDirectoryTree(file, indent + 1, sb)
    }
    else {
      printFile(file, indent + 1, sb)
    }
  }
}

private fun sortedFileList(dir: Path): List<Path> {
  return dir.directoryStreamIfExists {
    val list = ArrayList<Path>()
    it.mapTo(list) { it }
    list.sort()
    list
  } ?: emptyList()
}

private fun printFile(file: Path, indent: Int, sb: StringBuilder) {
  getIndentString(indent, sb)
  sb.append("\u251c\u2500\u2500")
  sb.append(file.fileName.toString())
  sb.append("\n")
}

private fun getIndentString(indent: Int, sb: StringBuilder) {
  for (i in 0..indent - 1) {
    sb.append("  ")
  }
}