// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.local.generator

import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.local.GeneratorContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.*
import com.intellij.openapi.file.VirtualFileUtil
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.Experimental
object AssetsProcessor {

  internal fun generateSources(context: GeneratorContext, templateProperties: Map<String, Any>) {
    generateSources(context.outputDirectory, context.assets, templateProperties + ("context" to context))
  }

  fun generateSources(
    outputDirectory: VirtualFile,
    assets: List<GeneratorAsset>,
    templateProperties: Map<String, Any>
  ): List<VirtualFile> {
    return assets.map { asset ->
      when (asset) {
        is GeneratorTemplateFile -> generateSources(outputDirectory, asset, templateProperties)
        is GeneratorResourceFile -> generateSources(outputDirectory, asset)
        is GeneratorEmptyDirectory -> generateSources(outputDirectory, asset)
      }
    }
  }

  private fun generateSources(outputDirectory: VirtualFile, asset: GeneratorTemplateFile, properties: Map<String, Any>): VirtualFile {
    val content = asset.getTextContent(properties)
    val file = findOrCreateFile(outputDirectory, asset.targetFileName)
    VirtualFileUtil.setTextContent(file, content)
    return file
  }

  private fun generateSources(outputDirectory: VirtualFile, asset: GeneratorResourceFile): VirtualFile {
    val content = asset.getBinaryContent()
    val file = findOrCreateFile(outputDirectory, asset.targetFileName)
    VirtualFileUtil.setBinaryContent(file, content)
    return file
  }

  private fun generateSources(outputDirectory: VirtualFile, asset: GeneratorEmptyDirectory): VirtualFile {
    return findOrCreateDirectory(outputDirectory, asset.targetFileName)
  }

  private fun GeneratorTemplateFile.getTextContent(properties: Map<String, Any>): String {
    return try {
      template.getText(properties)
    }
    catch (e: Throwable) {
      throw TemplateProcessingException(e)
    }
  }

  private fun GeneratorResourceFile.getBinaryContent(): ByteArray {
    return try {
      resource.openStream().use {
        it.readBytes()
      }
    }
    catch (e: Throwable) {
      throw ResourceProcessingException(e)
    }
  }

  private fun findOrCreateFile(outputDirectory: VirtualFile, relativePath: String): VirtualFile {
    logger<AssetsProcessor>().info("Creating file $relativePath in ${outputDirectory.path}")
    return VirtualFileUtil.findOrCreateFile(outputDirectory, relativePath)
  }

  private fun findOrCreateDirectory(outputDirectory: VirtualFile, relativePath: String): VirtualFile {
    logger<AssetsProcessor>().info("Creating directory $relativePath in ${outputDirectory.path}")
    return VirtualFileUtil.findOrCreateDirectory(outputDirectory, relativePath)
  }

  private class TemplateProcessingException(t: Throwable) : IOException("Unable to process template", t)
  private class ResourceProcessingException(t: Throwable) : IOException("Unable to process resource", t)
}