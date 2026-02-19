// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser

object FileSaverDescriptorFactory {


  /**
   * Creates a FileSaverDescriptor that allows the selection of a single file while disabling folder, JAR,
   * JAR contents, and multiple file selection.
   *
   * @return a preconfigured FileSaverDescriptor allowing selection of a single file.
   */
  @JvmStatic
  fun createSingleFileNoJarsDescriptor(): FileSaverDescriptor {
    return FileSaverDescriptor(true, false, false, false)
  }
}