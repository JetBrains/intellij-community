package com.intellij.ide.starters.local

import com.intellij.ide.fileTemplates.FileTemplate
import java.net.URL

sealed class GeneratorAsset {
  abstract val targetFileName: String
}

data class GeneratorTemplateFile(
  override val targetFileName: String,
  val template: FileTemplate
) : GeneratorAsset()

data class GeneratorResourceFile(
  override val targetFileName: String,
  val resource: URL
) : GeneratorAsset()

data class GeneratorEmptyDirectory(
  override val targetFileName: String
) : GeneratorAsset()