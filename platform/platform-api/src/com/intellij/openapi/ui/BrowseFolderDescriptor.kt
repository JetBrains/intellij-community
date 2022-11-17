// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

class BrowseFolderDescriptor(descriptor: FileChooserDescriptor) : FileChooserDescriptor(descriptor) {

  var convertTextToPath: ((String) -> String)? = null
  var convertPathToText: ((String) -> String)? = null
  var convertFileToText: ((VirtualFile) -> String)? = null

  companion object {

    fun FileChooserDescriptor.withTextToPathConvertor(convertor: (@Nls String) -> @NonNls String): BrowseFolderDescriptor {
      return asBrowseFolderDescriptor().apply {
        convertTextToPath = convertor
      }
    }


    fun FileChooserDescriptor.withPathToTextConvertor(convertor: (@NonNls String) -> @Nls String): BrowseFolderDescriptor {
      return asBrowseFolderDescriptor().apply {
        convertPathToText = convertor
      }
    }

    fun FileChooserDescriptor.withFileToTextConvertor(convertor: (VirtualFile) -> @Nls String): BrowseFolderDescriptor {
      return asBrowseFolderDescriptor().apply {
        convertFileToText = convertor
      }
    }

    @JvmStatic
    fun FileChooserDescriptor.asBrowseFolderDescriptor(): BrowseFolderDescriptor {
      return when (this) {
        is BrowseFolderDescriptor -> this
        else -> BrowseFolderDescriptor(this)
      }
    }
  }
}