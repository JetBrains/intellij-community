// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    fun from(vararg extensions: String): List<FileAssociation> =
      extensions.map { FileAssociation(it) }

    @JvmStatic
    fun from(extensionsToIcons: Map<String, String>): List<FileAssociation> =
      extensionsToIcons.entries.map { FileAssociation(it.key, it.value) }
  }
}
