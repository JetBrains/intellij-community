// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

/**
 * File association which installer will associate with the product.
 */
data class FileAssociation @JvmOverloads constructor(
  /**
   * File extension without leading dot
   */
  val extension: String,

  /**
   * Custom icon file for association. Product icon will be used by default.
   * Example: "$projectHome/ruby/ideResources/artwork/rubymine.icns"
   * Note: used only for MacOS
   */
  val iconPath: String = "",
) {
  companion object {
    @JvmStatic
    fun from(vararg extensions: String): List<FileAssociation> = extensions.map(::FileAssociation)

    @JvmStatic
    fun from(extensionsToIcons: Map<String, String>): List<FileAssociation> {
      return extensionsToIcons.entries.map { FileAssociation(it.key, it.value) }
    }
  }
}
