// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.icons.ImageResourceLocation
import org.jetbrains.icons.rendering.GenericImageResourceLoader


internal class ImageResourceLoaderExtension {
  @Attribute("loader")
  lateinit var loader: GenericImageResourceLoader

  @Attribute("location")
  lateinit var location: String

  companion object {
    fun getLoaderFor(location: ImageResourceLocation): GenericImageResourceLoader? {
      for (extension in LOADER_EP_NAME.extensionList) {
        if (location::class.qualifiedName == extension.location) {
          return extension.loader
        }
      }
      return null
    }

    val LOADER_EP_NAME: ExtensionPointName<ImageResourceLoaderExtension> = ExtensionPointName("com.intellij.icons.imageResourceLoader")
  }
}
