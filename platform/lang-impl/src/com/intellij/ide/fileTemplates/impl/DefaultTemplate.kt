// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.text.Strings
import com.intellij.reference.SoftReference
import com.intellij.util.LocalizationUtil
import com.intellij.util.ResourceUtil
import java.io.IOException
import java.lang.ref.Reference
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Path
import java.util.function.Function
import kotlin.io.path.invariantSeparatorsPathString

class DefaultTemplate(val name: String,
                      val extension: String,
                      private val textLoader: Function<String, String?>,
                      private val descriptionLoader: Function<String, String?>?,
                      private val descriptionPath: String?,
                      private val templatePath: Path) {
  private var text: Reference<String?>? = null

  //NON-NLS
  private var descriptionText: Reference<String>? = null

  @Deprecated("Use {@link #DefaultTemplate(String, String, Supplier, Function, String)}")
  constructor(name: String, extension: String, templateUrl: URL, descriptionUrl: URL?) : this(name, extension, Function<String, String?> {
    try {
      ResourceUtil.loadText(templateUrl.openStream())
    }
    catch (e: IOException) {
      logger<DefaultTemplate>().error(e)
      ""
    }
  }, if (descriptionUrl == null) null else Function {
    try {
      ResourceUtil.loadText(descriptionUrl.openStream())
    }
    catch (e: IOException) {
      logger<DefaultTemplate>().error(e)
      ""
    }
  }, null, Path.of(FileTemplatesLoader.TEMPLATES_DIR).resolve(FileTemplateBase.getQualifiedName(name, extension)))

  val qualifiedName: String
    get() = FileTemplateBase.getQualifiedName(name, this.extension)

  fun getText(): String {
    var text = SoftReference.dereference(this.text)
    if (text != null) return text
    val locale = LocalizationUtil.getLocaleFromPlugin()
    if (locale != null) {
      val localizedPaths = LocalizationUtil.getLocalizedPaths(templatePath).map { it.invariantSeparatorsPathString }
      for (path in localizedPaths) {
        text = textLoader.apply(path)?.let { Strings.convertLineSeparators(it) }
        if (!text.isNullOrEmpty()) break
      }
    }
    if (text == null) {
      text = textLoader.apply(templatePath.invariantSeparatorsPathString)
    }
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

    try {
      if (LocalizationUtil.getLocaleFromPlugin() != null && descriptionPath != null) {
          val localizedPaths = LocalizationUtil.getLocalizedPaths(Path.of(FileTemplatesLoader.TEMPLATES_DIR).resolve(descriptionPath))
          val localizedPathStrings = localizedPaths.map { it.invariantSeparatorsPathString }
          for (path in localizedPathStrings) {
            text = descriptionLoader.apply(path)?.let { Strings.convertLineSeparators(it) }
            if (text != null) break
          }
      }

      if (text == null) {
        // descriptionPath is null if deprecated constructor is used - in this case descriptionPath doesn't matter
        text = descriptionLoader.apply(descriptionPath ?: "")?.let { Strings.convertLineSeparators(it) }
      }
    }
    catch (e: IOException) {
      logger<DefaultTemplate>().info(e)
    }

    descriptionText = java.lang.ref.SoftReference(text)
    return text ?: ""
  }

  // the only external usage - https://github.com/wrdv/testme-idea/blob/8e314aea969619f43f0c6bb17f53f1d95b1072be/src/main/java/com/weirddev/testme/intellij/ui/template/FTManager.java#L200
  @Deprecated("Do not use.")
  fun getTemplateURL(): URL {
    try {
      return URL("https://not.relevant")
    }
    catch (e: MalformedURLException) {
      throw RuntimeException(e)
    }
  }

  override fun toString(): String = textLoader.toString()
}

