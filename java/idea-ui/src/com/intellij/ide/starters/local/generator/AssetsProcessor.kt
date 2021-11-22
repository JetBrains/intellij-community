// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.local.generator

import com.intellij.ide.starters.local.GeneratorContext
import com.intellij.ide.starters.local.GeneratorEmptyDirectory
import com.intellij.ide.starters.local.GeneratorResourceFile
import com.intellij.ide.starters.local.GeneratorTemplateFile
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VfsUtil
import java.io.IOException

internal class AssetsProcessor {
  fun generateSources(context: GeneratorContext, templateProps: Map<String, Any>) {
    val templateProperties = mutableMapOf<String, Any>()
    templateProperties["context"] = context
    templateProperties.putAll(templateProps)

    val log = logger<AssetsProcessor>()

    for (asset in context.assets) {
      val subPath = if (asset.targetFileName.contains("/"))
        "/" + asset.targetFileName.substringBeforeLast("/")
      else
        ""

      val outputDirectory = VfsUtil.createDirectoryIfMissing(context.outputDirectory.fileSystem, context.outputDirectory.path)
                            ?: throw IllegalStateException("Unable to create directory ${context.outputDirectory.path}")

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
          ?: throw IllegalStateException("Unable to create directory ${subPath} in ${context.outputDirectory.path}")
        }

        val fileName = asset.targetFileName.substringAfterLast("/")
        log.info("Creating file $fileName in ${fileDirectory.path}")

        when (asset) {
          is GeneratorTemplateFile -> {
            val sourceCode: String
            try {
              sourceCode = asset.template.getText(templateProperties)
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