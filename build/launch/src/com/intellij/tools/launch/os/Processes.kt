package com.intellij.tools.launch.os

import java.io.File

fun ProcessBuilder.affixIO(redirectOutputIntoParentProcess: Boolean, logFolder: File) {
  if (redirectOutputIntoParentProcess) {
    this.inheritIO()
  }
  else {
    logFolder.mkdirs()
    val ts = System.currentTimeMillis()
    this.redirectOutput(logFolder.resolve("out-$ts.log"))
    this.redirectError(logFolder.resolve("err-$ts.log"))
  }
}