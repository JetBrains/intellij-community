// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.text.Strings
import com.intellij.reference.SoftReference
import java.io.IOException
import java.lang.ref.Reference
import java.nio.file.Path
import java.util.function.Function
import kotlin.io.path.invariantSeparatorsPathString

class DefaultTemplate constructor(
  val name: String,
  val extension: String,
  private val textLoader: Function<String, String?>,
  private val descriptionLoader: Function<String, String?>?,
  private val descriptionPath: String?,
  private val templatePath: Path,
  @JvmField internal val pluginDescriptor: PluginDescriptor,
) {
  private var text: Reference<String?>? = null

  //NON-NLS
  private var descriptionText: Reference<String>? = null

  val qualifiedName: String
    get() = FileTemplateBase.getQualifiedName(name, this.extension)

  fun getText(): String {
    var text = SoftReference.dereference(this.text)
    if (text != null) return text
    text = textLoader.apply(templatePath.invariantSeparatorsPathString)?.let { Strings.convertLineSeparators(it) }
    this.text = java.lang.ref.SoftReference(text)
    if (text == null) {
      logger<DefaultTemplate>().error("Cannot find file template by path: $templatePath")
    }
    return text ?: ""
  }

  fun getDescriptionText(): String {
    if (descriptionLoader == null) {
      return ""
    }

    var text = SoftReference.dereference(descriptionText)
    if (text != null) {
      return text
    }

    val fullPath = descriptionPath?.let { Path.of(FileTemplatesLoader.TEMPLATES_DIR).resolve(it) }
    try {
      if (fullPath != null) {
        text = descriptionLoader.apply(fullPath.invariantSeparatorsPathString)?.let { Strings.convertLineSeparators(it) }
      }

      if (text == null) {
        // descriptionPath is null if deprecated constructor is used - in this case descriptionPath doesn't matter
        text = descriptionLoader.apply(fullPath?.invariantSeparatorsPathString ?: "")?.let { Strings.convertLineSeparators(it) }
      }
    }
    catch (e: IOException) {
      logger<DefaultTemplate>().info(e)
    }
    descriptionText = java.lang.ref.SoftReference(text)

    if (text == null) {
      logger<DefaultTemplate>().error("Cannot find file by path: $fullPath")
      return "Unexpected error occurred"
    }
    return text
  }

  override fun toString(): String = "$name: $extension ($templatePath)"
}

