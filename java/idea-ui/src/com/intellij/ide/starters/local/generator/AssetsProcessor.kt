// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.local.generator

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.file.CanonicalPathUtil.toNioPath
import com.intellij.openapi.file.NioPathUtil
import com.intellij.openapi.file.VirtualFileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Path

@ApiStatus.Experimental
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

abstract class AbstractAssetsProcessor : AssetsProcessor {
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
      setTextContent(file, content)
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
      setBinaryContent(file, content)
      return file
    }
    catch (e: Throwable) {
      throw ResourceProcessingException(e)
    }
  }

  private fun generateSources(outputDirectory: Path, asset: GeneratorEmptyDirectory): Path {
    return findOrCreateDirectory(outputDirectory, asset.targetFileName)
  }

  protected abstract fun setTextContent(file: Path, content: String)
  protected abstract fun setBinaryContent(file: Path, content: ByteArray)
  protected abstract fun findOrCreateFile(outputDirectory: Path, relativePath: String): Path
  protected abstract fun findOrCreateDirectory(outputDirectory: Path, relativePath: String): Path
}

private class TemplateProcessingException(t: Throwable) : IOException("Unable to process template", t)
private class ResourceProcessingException(t: Throwable) : IOException("Unable to process resource", t)

class AssetsProcessorImpl : AbstractAssetsProcessor() {
  @Suppress("SSBasedInspection")
  private val LOG: Logger = logger<AssetsProcessor>()

  override fun setTextContent(file: Path, content: String) {
    NioPathUtil.setTextContent(file, content)
  }

  override fun setBinaryContent(file: Path, content: ByteArray) {
    NioPathUtil.setBinaryContent(file, content)
  }

  override fun findOrCreateFile(outputDirectory: Path, relativePath: String): Path {
    LOG.info("Creating file $relativePath in $outputDirectory")
    return NioPathUtil.findOrCreateFile(outputDirectory, relativePath)
  }

  override fun findOrCreateDirectory(outputDirectory: Path, relativePath: String): Path {
    LOG.info("Creating directory $relativePath in $outputDirectory")
    return NioPathUtil.findOrCreateDirectory(outputDirectory, relativePath)
  }
}

@Suppress("TestOnlyProblems")
fun convertOutputLocationForTests(moduleContentRoot: VirtualFile): Path {
  if (ApplicationManager.getApplication().isUnitTestMode) {
    return TestFileSystemLocation(moduleContentRoot, Path.of(moduleContentRoot.name))
  }

  return moduleContentRoot.toNioPath()
}

@TestOnly
class TestFileSystemLocation(
  val virtualFile: VirtualFile,
  /**
   * Fake Path for debug-purpose only, should never be used for disk operations
   */
  val debugPath: Path
): Path by debugPath {
  override fun toString(): String {
    return "TestFileSystemLocation($debugPath)"
  }
}

@TestOnly
class TestAssetsProcessorImpl : AbstractAssetsProcessor() {
  override fun setTextContent(file: Path, content: String) {
    if (file is TestFileSystemLocation) {
      VirtualFileUtil.setTextContent(file.virtualFile, content)
    } else {
      NioPathUtil.setTextContent(file, content)
    }
  }

  override fun setBinaryContent(file: Path, content: ByteArray) {
    if (file is TestFileSystemLocation) {
      VirtualFileUtil.setBinaryContent(file.virtualFile, content)
    } else {
      NioPathUtil.setBinaryContent(file, content)
    }
  }

  override fun findOrCreateFile(outputDirectory: Path, relativePath: String): Path {
    if (outputDirectory is TestFileSystemLocation) {
      val vFile = VirtualFileUtil.findOrCreateFile(outputDirectory.virtualFile, relativePath)
      return TestFileSystemLocation(vFile, outputDirectory.debugPath.resolve(relativePath.toNioPath()))
    } else {
      return NioPathUtil.findOrCreateFile(outputDirectory, relativePath)
    }
  }

  override fun findOrCreateDirectory(outputDirectory: Path, relativePath: String): Path {
    if (outputDirectory is TestFileSystemLocation) {
      val vFile = VirtualFileUtil.findOrCreateDirectory(outputDirectory.virtualFile, relativePath)

      return TestFileSystemLocation(vFile, outputDirectory.debugPath.resolve(relativePath.toNioPath()))
    } else {
      return NioPathUtil.findOrCreateDirectory(outputDirectory, relativePath)
    }
  }
}