// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

/**
 * File association which installer will associate with the product.
 * Note that users won't be able to switch off some of these associations during installation
 * so include only types of files which users will definitely prefer to open by the product.
 */
@CompileStatic
class FileAssociation {
  /**
   * File extension without leading dot
   */
  final String extension

  /**
   * Custom icon file for association. Example:
   * "$projectHome/ruby/ideResources/artwork/rubymine.icns"
   */
  final String iconPath

  FileAssociation(String extension, String iconPath = '') {
    this.extension = extension
    this.iconPath = iconPath
  }

  static List<FileAssociation> from(String... extensions) {
    extensions.collect { String extension ->
      new FileAssociation(extension)
    }
  }

  static List<FileAssociation> from(Map<String, String> extensionsToIcons) {
    extensionsToIcons.entrySet().collect { Map.Entry<String, String> entry ->
      new FileAssociation(entry.key, entry.value)
    }
  }
}
