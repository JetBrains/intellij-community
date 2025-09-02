// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.worker.core.output

import com.intellij.util.lang.HashMapZipFile
import java.nio.file.Path
import java.util.*

fun createEmptyOutputSink(withAbi: Boolean): OutputSink {
  return OutputSink(
    fileToData = TreeMap(),
    abiFileToData = if (withAbi) TreeMap() else null,
    oldZipFile = null,
    oldAbiZipFile = null,
  )
}

fun createOutputSink(oldJar: Path?, oldAbiJar: Path?, withAbi: Boolean): OutputSink {
  if (oldJar == null) {
    require(oldAbiJar == null)
  }

  // read data from old JAR if exists to copy data
  val zipFile = (if (oldJar == null) null else HashMapZipFile.loadIfNotEmpty(oldJar)) ?: return createEmptyOutputSink(withAbi)
  var ok = false
  try {
    val fileToData = computeMap(zipFile, isAbiFile = false)

    val abiZipFile = HashMapZipFile.loadIfNotEmpty(requireNotNull(oldAbiJar) {
      "If ABI JAR is missing, you must perform a full rebuild (delete existing output JAR)"
    })
    val abiFileToData = if (abiZipFile == null) TreeMap() else computeMap(abiZipFile, isAbiFile = true)

    val outputSink = OutputSink(
      fileToData = fileToData,
      abiFileToData = abiFileToData,
      oldZipFile = zipFile,
      oldAbiZipFile = abiZipFile,
    )
    ok = true
    return outputSink
  }
  finally {
    if (!ok) {
      zipFile.close()
    }
  }
}

private fun computeMap(zipFile: HashMapZipFile, isAbiFile: Boolean): TreeMap<String, Any> {
  val fileToData = TreeMap<String, Any>()
  var ok = false
  try {
    for (entry in zipFile.entries) {
      if (entry.isDirectory) {
        continue
      }

      val name = entry.name
      if (name != "__index__" && (!isAbiFile || name != NODE_INDEX_FILENAME)) {
        fileToData.put(name, entry)
      }
    }
    ok = true
  }
  finally {
    if (!ok) {
      zipFile.close()
    }
  }
  return fileToData
}