// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.provider.osFamily
import java.nio.file.Path


internal enum class CheckFor(val file: String) {
  JDK("javac"), JRE("java")
}

/**
 * Which file name is used for java binary for an OS this [path] resides on
 */
@OptIn(EelDelicateApi::class)
internal fun getJavaFileName(path: Path, checkFor: CheckFor): @NlsSafe String = when (path.osFamily) {
  // It is important to use the right file name because of IJPL-217480
  EelOsFamily.Posix -> checkFor.file
  EelOsFamily.Windows -> checkFor.file + ".exe"
}