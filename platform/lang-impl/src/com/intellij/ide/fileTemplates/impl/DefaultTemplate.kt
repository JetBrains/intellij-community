// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl

import com.intellij.DynamicBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.reference.SoftReference
import com.intellij.util.ResourceUtil
import java.io.IOException
import java.lang.ref.Reference
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.function.Function
import java.util.function.Supplier

class DefaultTemplate(val name: String,
                      val extension: String,
                      private val textSupplier: Supplier<String>,
                      private val descriptionLoader: Function<String, String>?,
                      private val descriptionPath: String?) {
  private var text: Reference<String?>? = null

  //NON-NLS
  private var descriptionText: Reference<String>? = null

  @Deprecated("Use {@link #DefaultTemplate(String, String, Supplier, Function, String)}")
  constructor(name: String, extension: String, templateUrl: URL, descriptionUrl: URL?) : this(name, extension, Supplier<String> {
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
  }, null)

  val qualifiedName: String
    get() = FileTemplateBase.getQualifiedName(name, this.extension)

  fun getText(): String {
    var text = SoftReference.dereference(this.text)
    if (text == null) {
      text = StringUtil.convertLineSeparators(textSupplier.get())
      this.text = java.lang.ref.SoftReference(text)
    }
    return text
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
      val langBundleLoader = DynamicBundle.findLanguageBundle()?.pluginDescriptor?.pluginClassLoader
      if (langBundleLoader != null && descriptionPath != null) {
        text = ResourceUtil.getResourceAsBytes("${FileTemplatesLoader.TEMPLATES_DIR}/$descriptionPath", langBundleLoader)
          ?.toString(StandardCharsets.UTF_8)
      }

      if (text == null) {
        // descriptionPath is null if deprecated constructor is used - in this case descriptionPath doesn't matter
        text = Strings.convertLineSeparators(descriptionLoader.apply(descriptionPath ?: ""))
      }
    }
    catch (e: IOException) {
      logger<DefaultTemplate>().error(e)
      text = ""
    }

    descriptionText = java.lang.ref.SoftReference(text)
    return text!!
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

  override fun toString(): String = textSupplier.toString()
}

