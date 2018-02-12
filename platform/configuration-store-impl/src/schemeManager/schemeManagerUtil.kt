package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.LOG
import com.intellij.openapi.options.ExternalizableScheme

internal fun ExternalizableScheme.renameScheme(newName: String) {
  if (newName != name) {
    name = newName
    LOG.assertTrue(newName == name)
  }
}

internal inline fun catchAndLog(fileName: String, runnable: (fileName: String) -> Unit) {
  try {
    runnable(fileName)
  }
  catch (e: Throwable) {
    LOG.error("Cannot read scheme $fileName", e)
  }
}