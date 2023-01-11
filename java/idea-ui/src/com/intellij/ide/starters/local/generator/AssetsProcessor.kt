// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.local.generator

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.*
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.file.*
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface AssetsProcessor {

  fun generateSources(
    outputDirectory: Path,
    assets: List<GeneratorAsset>,
    templateProperties: Map<String, Any>
  ): List<Path>

  companion object {

    fun getInstance(): AssetsProcessor = service()
  }
}

@ApiStatus.Internal
open class AssetsProcessorImpl : AssetsProcessor {

  override fun generateSources(
    outputDirectory: Path,
    assets: List<GeneratorAsset>,
    templateProperties: Map<String, Any>
  ): List<Path> {
    return assets.map { asset ->
      when (asset) {
        is GeneratorTemplateFile -> generateSources(outputDirectory, asset, templateProperties)
        is GeneratorResourceFile -> generateSources(outputDirectory, asset)
        is GeneratorEmptyDirectory -> generateSources(outputDirectory, asset)
      }
    }
  }

  private fun generateSources(outputDirectory: Path, asset: GeneratorTemplateFile, properties: Map<String, Any>): Path {
    try {
      val templateManager = FileTemplateManager.getDefaultInstance()
      val defaultProperties = templateManager.defaultProperties
      val content = asset.template.getText(defaultProperties + properties)
      val file = findOrCreateFile(outputDirectory, asset.targetFileName)
      writeText(file, content)
      return file
    }
    catch (e: Throwable) {
      throw TemplateProcessingException(e)
    }
  }

  private fun generateSources(outputDirectory: Path, asset: GeneratorResourceFile): Path {
    try {
      val content = asset.resource.openStream().use { it.readBytes() }
      val file = findOrCreateFile(outputDirectory, asset.targetFileName)
      writeBytes(file, content)
      return file
    }
    catch (e: Throwable) {
      throw ResourceProcessingException(e)
    }
  }

  private fun generateSources(outputDirectory: Path, asset: GeneratorEmptyDirectory): Path {
    return findOrCreateDirectory(outputDirectory, asset.targetFileName)
  }

  protected open fun writeText(file: Path, content: String) {
    file.writeText(content)
  }

  protected open fun writeBytes(file: Path, content: ByteArray) {
    file.writeBytes(content)
  }

  protected open fun findOrCreateFile(outputDirectory: Path, relativePath: String): Path {
    LOG.info("Creating file $relativePath in $outputDirectory")
    val filePath = outputDirectory.getResolvedNioPath(relativePath)
    return filePath.findOrCreateNioFile()
  }

  protected open fun findOrCreateDirectory(outputDirectory: Path, relativePath: String): Path {
    LOG.info("Creating directory $relativePath in $outputDirectory")
    val directoryPath = outputDirectory.getResolvedNioPath(relativePath)
    return directoryPath.findOrCreateNioDirectory()
  }

  companion object {

    private val LOG: Logger = logger<AssetsProcessor>()
  }
}

private class TemplateProcessingException(t: Throwable) : IOException("Unable to process template", t)
private class ResourceProcessingException(t: Throwable) : IOException("Unable to process resource", t)
