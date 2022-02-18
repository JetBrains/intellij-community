// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.local.generator

import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.local.GeneratorContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.io.IOException


@ApiStatus.Experimental
class AssetsProcessor {

  internal fun generateSources(context: GeneratorContext, templateProperties: Map<String, Any>) {
    generateSources(context.outputDirectory, context.assets, templateProperties + ("context" to context))
  }

  fun generateSources(outputDirectory: VirtualFile, assets: List<GeneratorAsset>, templateProperties: Map<String, Any>) {
    val outputDir = VfsUtil.createDirectoryIfMissing(outputDirectory.fileSystem, outputDirectory.path)
                    ?: throw IllegalStateException("Unable to create directory ${outputDirectory.path}")

    for (asset in assets) {
      when (asset) {
        is GeneratorTemplateFile -> generateSources(outputDir, asset, templateProperties)
        is GeneratorResourceFile -> generateSources(outputDir, asset)
        is GeneratorEmptyDirectory -> generateSources(outputDir, asset)
      }
    }
  }

  private fun createFile(outputDirectory: VirtualFile, relativePath: String): VirtualFile {
    val subPath = if (relativePath.contains("/"))
      "/" + relativePath.substringBeforeLast("/")
    else
      ""

    val fileDirectory = if (subPath.isEmpty()) {
      outputDirectory
    }
    else {
      VfsUtil.createDirectoryIfMissing(outputDirectory, subPath)
      ?: throw IllegalStateException("Unable to create directory ${subPath} in ${outputDirectory.path}")
    }

    val fileName = relativePath.substringAfterLast("/")
    LOG.info("Creating file $fileName in ${fileDirectory.path}")
    return fileDirectory.findOrCreateChildData(this, fileName)
  }

  private fun generateSources(outputDirectory: VirtualFile, asset: GeneratorTemplateFile, templateProps: Map<String, Any>) {
    val sourceCode = try {
      asset.template.getText(templateProps)
    }
    catch (e: Exception) {
      throw TemplateProcessingException(e)
    }
    val file = createFile(outputDirectory, asset.targetFileName)
    VfsUtil.saveText(file, sourceCode)
  }

  private fun generateSources(outputDirectory: VirtualFile, asset: GeneratorResourceFile) {
    val file = createFile(outputDirectory, asset.targetFileName)
    asset.resource.openStream().use {
      file.setBinaryContent(it.readBytes())
    }
  }

  private fun generateSources(outputDirectory: VirtualFile, asset: GeneratorEmptyDirectory) {
    LOG.info("Creating empty directory ${asset.targetFileName} in ${outputDirectory.path}")
    VfsUtil.createDirectoryIfMissing(outputDirectory, asset.targetFileName)
  }

  private class TemplateProcessingException(t: Throwable) : IOException("Unable to process template", t)

  companion object {
    private val LOG = logger<AssetsProcessor>()
  }
}