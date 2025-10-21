// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OptionsDirectoryProcessor")
@file:OptIn(ExperimentalPathApi::class)

package com.intellij.compiler.server

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.EelProvider.Companion.EP_NAME
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.EelPathUtils.transferLocalContentToRemote
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

private val ENVIRONMENT_ASSOCIATED_FILENAMES = setOf("jdk.table.xml", "applicationLibraries.xml")

internal fun transferOptionsToRemote(optionsDir: Path, eelDescriptor: EelDescriptor): Path {
  if (!Registry.`is`("ide.workspace.model.per.environment.model.separation", true)) {
    return transferLocalContentToRemote(optionsDir, EelPathUtils.TransferTarget.Temporary(eelDescriptor))
  }
  val machine = eelDescriptor.machine
  val internalName = EP_NAME.extensionList.firstNotNullOfOrNull { provider ->
    provider.getInternalName(machine)
  }
  if (internalName == null) {
    return transferLocalContentToRemote(optionsDir, EelPathUtils.TransferTarget.Temporary(eelDescriptor))
  }
  val parentForProcessedOptionsDir = Files.createTempDirectory(internalName)
  try {
    prepareOptionsDirForEnvironment(optionsDir, parentForProcessedOptionsDir, internalName)
    return transferLocalContentToRemote(parentForProcessedOptionsDir, EelPathUtils.TransferTarget.Temporary(eelDescriptor))
  }
  finally {
    runCatching { parentForProcessedOptionsDir.deleteRecursively() }
  }
}

private fun prepareOptionsDirForEnvironment(
  originalOptionsDir: Path,
  parentForProcessedOptionsDir: Path,
  internalEnvironmentName: String,
) {
  val processedOptionsDir = originalOptionsDir.copyToRecursively(
    target = parentForProcessedOptionsDir,
    followLinks = false,
    copyAction = copyAction(skipFiles = ENVIRONMENT_ASSOCIATED_FILENAMES)
  )
  for (filename in ENVIRONMENT_ASSOCIATED_FILENAMES) {
    val originalFile = originalOptionsDir.resolve(internalEnvironmentName).resolve(filename)
    if (Files.exists(originalFile)) {
      originalFile.copyTo(processedOptionsDir.resolve(filename), overwrite = true)
    }
  }
}

@Suppress("SameParameterValue")
private fun copyAction(skipFiles: Set<String>): CopyActionContext.(source: Path, target: Path) -> CopyActionResult = { src, dst ->
  if (src.name !in skipFiles) src.copyToIgnoringExistingDirectory(dst, followLinks = false)
  else CopyActionResult.CONTINUE
}