// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OptionsDirectoryProcessor")
@file:OptIn(ExperimentalPathApi::class)

package com.intellij.compiler.server

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.getEelMachine
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.EelPathUtils.transferLocalContentToRemote
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.CopyActionContext
import kotlin.io.path.CopyActionResult
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
import kotlin.io.path.deleteRecursively
import kotlin.io.path.name

private val ENVIRONMENT_ASSOCIATED_FILENAMES = setOf("jdk.table.xml", "applicationLibraries.xml")

internal fun transferOptionsToRemote(optionsDir: Path, project: Project): Path {
  val eelDescriptor = project.getEelDescriptor()
  val machine = project.getEelMachine()

  if (!Registry.`is`("ide.workspace.model.per.environment.model.separation", false)) {
    return transferLocalContentToRemote(optionsDir, EelPathUtils.TransferTarget.Temporary(eelDescriptor))
  }
  val internalName = machine.internalName
  if (internalName == LocalEelMachine.internalName) {
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