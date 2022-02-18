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

  fun generateSources(outputDir: VirtualFile, assets: List<GeneratorAsset>, templateProps: Map<String, Any>) {
    val log = logger<AssetsProcessor>()

    for (asset in assets) {
      val subPath = if (asset.targetFileName.contains("/"))
        "/" + asset.targetFileName.substringBeforeLast("/")
      else
        ""

      val outputDirectory = VfsUtil.createDirectoryIfMissing(outputDir.fileSystem, outputDir.path)
                            ?: throw IllegalStateException("Unable to create directory ${outputDir.path}")

      if (asset is GeneratorEmptyDirectory) {
        log.info("Creating empty directory ${asset.targetFileName} in ${outputDirectory.path}")
        VfsUtil.createDirectoryIfMissing(outputDirectory, asset.targetFileName)
      }
      else {
        val fileDirectory = if (subPath.isEmpty()) {
          outputDirectory
        }
        else {
          VfsUtil.createDirectoryIfMissing(outputDirectory, subPath)
          ?: throw IllegalStateException("Unable to create directory ${subPath} in ${outputDirectory.path}")
        }

        val fileName = asset.targetFileName.substringAfterLast("/")
        log.info("Creating file $fileName in ${fileDirectory.path}")

        when (asset) {
          is GeneratorTemplateFile -> {
            val sourceCode: String
            try {
              sourceCode = asset.template.getText(templateProps)
            }
            catch (e: Exception) {
              throw TemplateProcessingException(e)
            }
            val file = fileDirectory.findOrCreateChildData(this, fileName)
            VfsUtil.saveText(file, sourceCode)
          }
          is GeneratorResourceFile -> {
            val file = fileDirectory.findOrCreateChildData(this, fileName)
            asset.resource.openStream().use {
              file.setBinaryContent(it.readBytes())
            }
          }
          else -> {
            throw UnsupportedOperationException("Unsupported asset type")
          }
        }
      }
    }
  }

  private class TemplateProcessingException(t: Throwable) : IOException("Unable to process template", t)
}