package com.intellij.configurationStore

import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.isDirectory
import java.nio.file.Path

fun printDirectoryTree(folder: Path): String {
  if (!folder.isDirectory()) {
    throw IllegalArgumentException("folder is not a Directory")
  }
  val indent = 0
  val sb = StringBuilder()
  printDirectoryTree(folder, indent, sb)
  return sb.toString()
}

private fun printDirectoryTree(dir: Path, indent: Int, sb: StringBuilder) {
  if (!dir.isDirectory()) {
    throw IllegalArgumentException("folder is not a Directory")
  }
  getIndentString(indent, sb)
  sb.append("\u251c\u2500\u2500")
  sb.append(dir.fileName.toString())
  sb.append("/")
  sb.append("\n")
  for (file in dir.directoryStreamIfExists { it.map { it } }!!) {
    if (file.isDirectory()) {
      printDirectoryTree(file, indent + 1, sb)
    }
    else {
      printFile(file, indent + 1, sb)
    }
  }
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