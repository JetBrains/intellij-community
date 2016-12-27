package com.intellij.configurationStore

import java.io.File

fun printDirectoryTree(folder: File): String {
  if (!folder.isDirectory) {
    throw IllegalArgumentException("folder is not a Directory")
  }
  val indent = 0
  val sb = StringBuilder()
  printDirectoryTree(folder, indent, sb)
  return sb.toString()
}

private fun printDirectoryTree(folder: File, indent: Int, sb: StringBuilder) {
  if (!folder.isDirectory) {
    throw IllegalArgumentException("folder is not a Directory")
  }
  getIndentString(indent, sb)
  sb.append("\u251c\u2500\u2500")
  sb.append(folder.name)
  sb.append("/")
  sb.append("\n")
  for (file in folder.listFiles()!!) {
    if (file.isDirectory) {
      printDirectoryTree(file, indent + 1, sb)
    }
    else {
      printFile(file, indent + 1, sb)
    }
  }
}

private fun printFile(file: File, indent: Int, sb: StringBuilder) {
  getIndentString(indent, sb)
  sb.append("\u251c\u2500\u2500")
  sb.append(file.name)
  sb.append("\n")
}

private fun getIndentString(indent: Int, sb: StringBuilder) {
  for (i in 0..indent - 1) {
    sb.append("  ")
  }
}