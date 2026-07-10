// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApiBlocking
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Experimental
object EelSystemFolderUtils {
  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun getSystemFolder(project: Project): Path = getSystemFolder(project.getEelDescriptor().toEelApiBlocking())

  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun getSystemFolder(descriptor: EelDescriptor): Path = getSystemFolder(descriptor.toEelApiBlocking())

  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun getSystemFolder(eel: EelApi): Path {
    val selector = PathManager.getPathsSelector() ?: "IJ-Platform"
    val userHomeFolder = eel.userInfo.home.asNioPath().toString()
    return Path.of(PathManager.getDefaultSystemPathFor(eel.platform.toOs(), userHomeFolder, selector, eel.exec.fetchLoginShellEnvVariablesBlocking()))
  }
}
