// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.local.generator

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorEmptyDirectory
import com.intellij.ide.starters.local.GeneratorFile
import com.intellij.ide.starters.local.GeneratorResourceFile
import com.intellij.ide.starters.local.GeneratorTemplateFile
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.findOrCreateDirectory
import com.intellij.openapi.util.io.findOrCreateFile
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.*

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface AssetsProcessor {

  fun generateSources(
    outputDirectory: Path,
    assets: List<GeneratorAsset>,
    templateProperties: Map<String, Any>,
  ): List<Path>

  companion object {

    @JvmStatic
    fun getInstance(): AssetsProcessor = service()
  }
}

@ApiStatus.Internal
open class AssetsProcessorImpl : AssetsProcessor {

  override fun generateSources(
    outputDirectory: Path,
    assets: List<GeneratorAsset>,
    templateProperties: Map<String, Any>,
  ): List<Path> {
    return assets.map { asset ->
      when (asset) {
        is GeneratorFile -> generateSources(outputDirectory, asset)
        is GeneratorTemplateFile -> generateSources(outputDirectory, asset, templateProperties)
        is GeneratorResourceFile -> generateSources(outputDirectory, asset)
        is GeneratorEmptyDirectory -> generateSources(outputDirectory, asset)
      }
    }
  }

  private fun generateSources(outputDirectory: Path, asset: GeneratorFile): Path {
    try {
      val file = findOrCreateFile(outputDirectory, asset.relativePath)
      addPosixFilePermissions(file, asset.permissions)
      writeBytes(file, asset.content)
      return file
    }
    catch (e: IOException) {
      throw FileProcessingException(e)
    }
  }

  private fun generateSources(outputDirectory: Path, asset: GeneratorTemplateFile, properties: Map<String, Any>): Path {
    try {
      val templateManager = FileTemplateManager.getDefaultInstance()
      val defaultProperties = templateManager.defaultProperties
      val content = asset.template.getText(defaultProperties + properties)
      val file = findOrCreateFile(outputDirectory, asset.relativePath)
      addPosixFilePermissions(file, asset.permissions)
      writeText(file, content)
      return file
    }
    catch (e: IOException) {
      throw TemplateProcessingException(e)
    }
  }

  private fun generateSources(outputDirectory: Path, asset: GeneratorResourceFile): Path {
    try {
      val content = asset.resource.openStream().use { it.readBytes() }
      val file = findOrCreateFile(outputDirectory, asset.relativePath)
      addPosixFilePermissions(file, asset.permissions)
      writeBytes(file, content)
      return file
    }
    catch (e: IOException) {
      throw ResourceProcessingException(e)
    }
  }

  private fun generateSources(outputDirectory: Path, asset: GeneratorEmptyDirectory): Path {
    try {
      val file = findOrCreateDirectory(outputDirectory, asset.relativePath)
      addPosixFilePermissions(file, asset.permissions)
      return file
    }
    catch (e: IOException) {
      throw EmptyDirectoryProcessingException(e)
    }
  }

  protected open fun writeText(path: Path, content: String) {
    path.writeText(content)
  }

  protected open fun writeBytes(path: Path, content: ByteArray) {
    path.writeBytes(content)
  }

  protected open fun findOrCreateFile(path: Path, relativePath: String): Path {
    LOG.info("Creating file $relativePath in $path")
    return path.findOrCreateFile(relativePath)
  }

  protected open fun findOrCreateDirectory(path: Path, relativePath: String): Path {
    LOG.info("Creating directory $relativePath in $path")
    return path.findOrCreateDirectory(relativePath)
  }

  protected open fun addPosixFilePermissions(path: Path, permissions: Set<PosixFilePermission>) {
    if (path.fileStore().supportsFileAttributeView(PosixFileAttributeView::class.java))
      path.setPosixFilePermissions(path.getPosixFilePermissions() + permissions)
  }

  companion object {

    private val LOG: Logger = logger<AssetsProcessor>()
  }
}

private class FileProcessingException(t: Throwable) : IOException("Unable to generate file", t)
private class TemplateProcessingException(t: Throwable) : IOException("Unable to generate file by template", t)
private class ResourceProcessingException(t: Throwable) : IOException("Unable to generate file by resource", t)
private class EmptyDirectoryProcessingException(t: Throwable) : IOException("Unable to generate empty directory", t)
